# ShopForge — Roadmap to MVP Launch

## Context

ShopForge has an approved SRS, DB design doc, and dev guide (`docs/01_SRS_ShopForge.pdf`, `02_DB_Design_ShopForge.pdf`, `03_Dev_Guide_ShopForge.pdf`) describing a full B2C ecommerce platform. Auth, product catalog, categories, and user/address management are already built and working end-to-end (backend + frontend). Everything past that — cart, checkout, orders, payments, reviews, coupons, wishlist, admin ops — exists only as UI mockups on the frontend (hardcoded/mock data, no backend calls) even though the **database schema for nearly all of it already exists**.

Concretely, `V1__init.sql` already creates `carts`, `cart_items`, `orders`, `order_items`, `order_status_history`, `payments`, `refunds`, `shipments`, `coupons`, `reviews`, `notifications`, and `addresses`, and the matching JPA entities already exist under `entities/`. What's missing is the layer on top: repositories, services, controllers, DTOs, and the frontend wiring to replace the mock data with real API calls. Only **wishlist** has no schema at all yet.

> Note: the root `README.md`'s "Project Roadmap & Sprint Milestones" section currently marks Sprint 3 (Cart, Wishlist & Addresses) as "Completed ✅" — that's inaccurate. Cart is a client-only Zustand store never synced to a backend, and wishlist has no backend or store at all. This roadmap reflects the actual state of the codebase, verified by reading the controllers/entities/migrations directly.

This roadmap sequences the remaining work as vertical, end-to-end feature slices (backend + frontend together per feature) so the store becomes actually functional as early as possible, rather than building all backend modules first. Scope decisions made for this plan: target a **pragmatic MVP** (defer Redis/Elasticsearch/RabbitMQ/SMS-OTP/multi-currency to a backlog), a **single-store, admin-operated** model (no seller-scoped marketplace), and **Razorpay + COD only** for payments.

Every new module should follow the conventions already established by `ProductService`/`ProductController`:
- Controllers return `ResponseEntity<ApiResponse<T>>` (`common/response/ApiResponse.java`); errors throw `AppException(message, HttpStatus)`, caught by `GlobalExceptionHandler`.
- Services are `@Service @RequiredArgsConstructor` with constructor-injected `final` repos; mutating methods `@Transactional`; entity→DTO mapping lives in private `mapToXxxResponse` helpers.
- List endpoints take plain `@RequestParam int page/size`, build `Sort`/`PageRequest` in the service, and return a flattened `XxxListResponseDto` (content, pageNumber, pageSize, totalElements, totalPages, last) — not a raw Spring `Page`.
- Authenticated user is obtained via `@AuthenticationPrincipal CustomUserDetails userDetails` → `userDetails.getUser()`.
- Frontend API modules follow `lib/api/products.ts`: typed interfaces + `async` functions that call `api.get/post<{success,data}>(...)` and unwrap `.data.data`; pages consume them via `useQuery({queryKey: [...], queryFn})`.

## Phase 0 — Foundation cleanup (small, do first)

- Rotate and externalize the credentials found during the earlier security audit (DB password, Gmail app password, Google OAuth client secret, JWT default) — move to env vars, `.gitignore` the `client_secret_*.json` files in both repo roots.
- Add to `pom.xml`: `springdoc-openapi-starter-webmvc-ui` (the security config already permits `/swagger-ui/**` and `/api-docs/**` but nothing serves them), and the Razorpay Java SDK (`com.razorpay:razorpay-java`).
- Add a root-level `docker-compose.yml` (Postgres + Mailhog) so local setup doesn't depend on a manually-installed Postgres — mirrors the dev guide's compose file, adjusted to drop Redis (deferred).

## Phase 1 — Cart (backend + frontend)

**Backend:** `CartRepository`, `CartItemRepository`, `CartService`, `CartController` under `/api/v1/cart` (`GET /`, `POST /items`, `PATCH /items/{id}`, `DELETE /items/{id}`, `DELETE /` for clear). One active cart per logged-in user (`carts.user_id`); stock validation against `inventory.quantity_available` on add, mirroring the dev guide's `validateCartStock` pattern. Reuse `AppException` for out-of-stock/not-found.

**Frontend:** New `lib/api/cart.ts` following the `products.ts` pattern. Keep `store/cartStore.ts` as the guest/optimistic-UI cart, but make it sync to the backend once a session exists: on login, merge local cart items into the server cart (call `POST /items` for each local line — no need for a `session_id`-based guest cart server-side since NextAuth already knows when a session starts); on every mutation while logged in, call the API instead of (or in addition to) local state. `cart/page.tsx` and the `CartDrawer` keep their existing UI, just swap local-only actions for the new API calls.

## Phase 2 — Checkout & Orders (backend + frontend)

**Backend:** `OrderRepository`, `OrderItemRepository`, `OrderStatusHistoryRepository`, `CheckoutService`, `OrderService`, `OrderController` under `/api/v1/orders`. Checkout endpoint (`POST /api/v1/orders`) follows the dev guide's transactional flow: lock/load cart → validate & reserve stock (write `inventory_transactions` rows, decrement `quantity_on_hand` — never trust client-sent prices/totals, recompute server-side from `product_variants`) → create `Order` + `OrderItem`s with a `product_snapshot` JSONB (order number is auto-generated by the existing DB trigger) → clear cart. `GET /`, `GET /{id}` for customer order history/detail; `PATCH /{id}/status` for admin transitions, guarded by the `ALLOWED_TRANSITIONS` state-machine map from the dev guide, writing to `order_status_history`.

**Frontend:** New `lib/api/orders.ts`. Replace `checkout/page.tsx`'s fake `setTimeout` + random order ID with a real `POST /api/v1/orders` call (address step already has real data available from `UserController`'s `/me/addresses`, just needs wiring instead of the hardcoded array). Add an order confirmation view and wire `account/page.tsx`'s "Order History" tab to `GET /api/v1/orders` instead of `mockOrders`.

## Phase 3 — Payments: Razorpay + COD (backend + frontend)

**Backend:** `PaymentRepository`, `RefundRepository`, `PaymentService`, `PaymentController` under `/api/v1/payments`. `POST /initiate` creates a Razorpay order (SDK added in Phase 0) and a local `payments` row with a generated `idempotency_key` (column already exists, `UNIQUE`). `POST /webhook` verifies the HMAC-SHA256 signature before processing, exactly as in the dev guide's `handleWebhook` example, then transitions the order to `PAYMENT_RECEIVED` via `OrderService`. COD is a synchronous path: `PaymentService` marks the payment `SUCCESS` immediately and the order `PROCESSING`. Trigger the "order placed" / "payment confirmed" emails here (extend the existing `EmailService`, following its current `sendOrderShipped`-style template pattern).

**Frontend:** Add the Razorpay checkout.js modal to the checkout page's payment step; COD becomes a plain "Place Order" button hitting the same order-creation + payment-initiate flow.

## Phase 4 — Order management, notifications, wishlist

- **Order management:** customer-facing cancel (before `SHIPPED`) and admin fulfillment endpoints (update tracking number → writes `shipments`, bulk status update) on `OrderController`; wire `admin/page.tsx`'s "Fulfillment Queue" tab to real data instead of `adminOrders`.
- **Notifications:** `NotificationRepository`/`NotificationService`, a simple `GET /api/v1/notifications` (unread-first, using the existing partial index) for an in-app bell/dropdown; email sends from Phase 3 also insert a `Notification` row.
- **Wishlist (new — no existing schema):** add `V3__create_wishlists.sql` (`wishlists`, `wishlist_items`, single default wishlist per user per the SRS) + entities + `WishlistRepository`/`Service`/`Controller` at `/api/v1/wishlists`. Frontend: new `store/wishlistStore.ts` + `lib/api/wishlist.ts`, wired into the existing heart icons in `ProductCard.tsx`, `p/[slug]/page.tsx`, `Navbar.tsx`, and `account/page.tsx`'s wishlist tab (all currently mock-only).

## Phase 5 — Reviews & Coupons

- **Reviews:** `ReviewRepository`/`Service`/`Controller` at `/api/v1/reviews`. Verified-purchase check via `order_items` (user must have a delivered `OrderItem` for the product); for MVP, auto-approve on creation (skip the moderation queue — the `review_status` column and admin moderation endpoint are backlog, the schema already supports adding it later). Product's `avg_rating`/`review_count` update automatically via the existing DB trigger. Frontend: replace the mock `product.reviews` in `p/[slug]/page.tsx` with real data + a review submission form gated on verified purchase.
- **Coupons:** `CouponRepository`/`Service`/`Controller`. Implement the validation engine exactly as in the dev guide (`validateCoupon`: time window, usage limit, per-user limit, min order value) at `POST /api/v1/coupons/validate`, called from the checkout page's coupon input (currently a UI stub); admin CRUD (`POST/GET /api/v1/coupons`) for creating campaigns, surfaced as a new tab in `admin/page.tsx`.

## Phase 6 — Admin dashboard (real data)

Add `AdminController`/`AdminService` at `/api/v1/admin` with aggregation queries (SQL `GROUP BY`/`SUM` over `orders`/`order_items`) for: today's/weekly/monthly revenue, order counts by status, top-selling products, low-stock list (already have `inventory.reorder_threshold`). Replace the hardcoded KPI numbers and the bar-chart mock data in `admin/page.tsx`'s Overview tab with these real endpoints.

## Phase 7 — Hardening

- Rate limiting (Bucket4j) on `/api/v1/auth/**` per the SRS's brute-force requirement.
- Test coverage for the new services (JUnit + Mockito, following the pattern implied by the existing `AuthIntegrationTest`) — at minimum: checkout stock-reservation race handling, coupon validation edge cases, payment webhook idempotency (replay should no-op).
- NextAuth type augmentation (`declare module 'next-auth'`) to remove the scattered `@ts-ignore`/`@ts-expect-error` on `session.user.roles`/`accessToken` access.
- Basic GitHub Actions CI (build + test on push), using the Phase 0 docker-compose Postgres service.

## Explicit backlog (deferred, not part of this roadmap)

Redis caching, Elasticsearch, RabbitMQ/async event bus, SMS OTP, Stripe/international payments, multi-seller marketplace (seller-scoped auth + dashboard), review moderation queue, GDPR export/delete tooling, formal PCI-DSS/WCAG audits, DB read replicas, S3/CDN image pipeline (images remain pasted URLs as they are today).

## Verification per phase

- Backend: `mvn test` after each module; manually exercise new endpoints via the already-permitted `/swagger-ui` once Phase 0 adds springdoc.
- Frontend: `npm run dev`, walk the golden path in-browser after each phase (Phase 1: add-to-cart persists across refresh/login; Phase 2: full checkout produces a real order visible in "Order History"; Phase 3: Razorpay test-mode payment completes and flips order status; Phase 4: wishlist add/remove persists, admin fulfillment queue shows real orders; Phase 5: a review only submits after a delivered order, coupon rejects when conditions aren't met; Phase 6: KPI numbers match what's actually in the DB).
- Re-run the credential rotation check from Phase 0 before any deployment.
