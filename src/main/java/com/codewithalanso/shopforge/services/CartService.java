package com.codewithalanso.shopforge.services;

import com.codewithalanso.shopforge.common.exception.AppException;
import com.codewithalanso.shopforge.dto.cart.AddCartItemRequest;
import com.codewithalanso.shopforge.dto.cart.CartItemResponse;
import com.codewithalanso.shopforge.dto.cart.CartResponse;
import com.codewithalanso.shopforge.dto.cart.UpdateCartItemRequest;
import com.codewithalanso.shopforge.entities.Cart;
import com.codewithalanso.shopforge.entities.CartItem;
import com.codewithalanso.shopforge.entities.Product;
import com.codewithalanso.shopforge.entities.ProductImage;
import com.codewithalanso.shopforge.entities.ProductVariant;
import com.codewithalanso.shopforge.entities.User;
import com.codewithalanso.shopforge.repositories.CartItemRepository;
import com.codewithalanso.shopforge.repositories.CartRepository;
import com.codewithalanso.shopforge.repositories.InventoryRepository;
import com.codewithalanso.shopforge.repositories.ProductVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Business logic for the shopping cart. This is the "service layer" -- the piece that sits
 * between the controller (which only knows about HTTP: paths, JSON, status codes) and the
 * repositories (which only know about the database). Controllers should stay thin and just
 * delegate here; all the actual decisions -- "is there enough stock?", "does this item already
 * exist in the cart?" -- belong in a service, not scattered across controller methods.
 *
 * @RequiredArgsConstructor (Lombok) generates a constructor that takes every `final` field
 * below as a parameter. Spring sees there's exactly one constructor and uses it to inject the
 * four repository beans automatically when it creates this CartService bean -- this is
 * "constructor injection", the recommended way to wire dependencies in Spring (as opposed to
 * the older @Autowired-on-a-field style), because it makes every dependency explicit and lets
 * you construct a CartService by hand in a plain unit test with no Spring container at all.
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryRepository inventoryRepository;

    /** Matches the SRS's "cart expiry: 30 days inactive" requirement -- see getOrCreateCart. */
    private static final long CART_EXPIRY_DAYS = 30;

    /**
     * Fetch the current user's cart (creating an empty one on first use).
     *
     * Why @Transactional here even though nothing is being written: application.properties has
     * `spring.jpa.open-in-view=false`, which means Hibernate's session normally closes the
     * instant a repository call returns -- it does NOT stay open for the rest of the request.
     * mapToResponse() below walks cart -> items -> variant -> product -> images, and every one
     * of those is a @ManyToOne/@OneToMany marked FetchType.LAZY, meaning Hibernate only loads it
     * the first time you actually call the getter, not upfront. Without an open session at that
     * moment, that lazy-load throws LazyInitializationException. @Transactional keeps the
     * session open for the whole method body, so by the time mapToResponse touches those lazy
     * fields, the session is still there to fetch them. readOnly=true is just an optimization
     * hint (skip Hibernate's dirty-checking) for methods that never modify anything.
     */
    @Transactional(readOnly = true)
    public CartResponse getCart(User user) {
        return mapToResponse(getOrCreateCart(user));
    }

    /**
     * Add a variant to the cart, or increase its quantity if it's already there.
     */
    @Transactional
    public CartResponse addItem(User user, AddCartItemRequest request) {
        Cart cart = getOrCreateCart(user);

        ProductVariant variant = productVariantRepository.findById(request.getVariantId())
                .orElseThrow(() -> new AppException("Product variant not found", HttpStatus.NOT_FOUND));

        if (!variant.isActive()) {
            throw new AppException("This item is no longer available", HttpStatus.CONFLICT);
        }

        // cart_items has a UNIQUE(cart_id, variant_id) constraint at the DB level (see
        // CartItem's @Table(uniqueConstraints=...)) -- adding a variant that's already in the
        // cart must update that existing row's quantity, never insert a second one.
        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> item.getVariant().getId().equals(variant.getId()))
                .findFirst();

        int currentQuantityInCart = existingItem.map(CartItem::getQuantity).orElse(0);
        int desiredQuantity = currentQuantityInCart + request.getQuantity();

        assertStockAvailable(variant, desiredQuantity);

        if (existingItem.isPresent()) {
            existingItem.get().setQuantity(desiredQuantity);
        } else {
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .variant(variant)
                    .quantity(request.getQuantity())
                    .unitPrice(effectivePrice(variant))
                    .build();
            // Cart.items is declared with cascade = CascadeType.ALL and orphanRemoval = true
            // (see the Cart entity). That means: adding a CartItem to this Set and then saving
            // the *Cart* is enough for Hibernate to INSERT the new cart_items row itself -- you
            // never have to call cartItemRepository.save(newItem) directly. This pattern (one
            // entity "owns" a collection of children that only ever exist through it) is called
            // an aggregate, and Cart is the "aggregate root" here.
            cart.getItems().add(newItem);
        }

        touchExpiry(cart);
        Cart saved = cartRepository.save(cart);
        return mapToResponse(saved);
    }

    /**
     * Change the quantity of one existing cart line.
     */
    @Transactional
    public CartResponse updateItemQuantity(User user, UUID itemId, UpdateCartItemRequest request) {
        CartItem item = findOwnedItem(user, itemId);
        assertStockAvailable(item.getVariant(), request.getQuantity());

        item.setQuantity(request.getQuantity());
        // Technically, because `item` is a managed entity loaded inside this @Transactional
        // method, Hibernate's "dirty checking" would persist this change automatically when the
        // transaction commits, even without calling save() explicitly. We call it anyway to
        // match the style used elsewhere in this codebase (see ProductService) and because it
        // makes the intent obvious to read, rather than relying on that implicit behaviour.
        cartItemRepository.save(item);

        touchExpiry(item.getCart());
        return mapToResponse(item.getCart());
    }

    /**
     * Remove one line from the cart entirely.
     */
    @Transactional
    public CartResponse removeItem(User user, UUID itemId) {
        CartItem item = findOwnedItem(user, itemId);
        Cart cart = item.getCart();

        // Removing from the parent's managed collection -- combined with orphanRemoval = true
        // on Cart.items -- is what tells Hibernate to DELETE the cart_items row. Calling
        // cartItemRepository.delete(item) directly would also work, but going through the
        // parent collection keeps Cart as the single aggregate root managing its own items,
        // which is the same reasoning as in addItem() above.
        cart.getItems().remove(item);

        touchExpiry(cart);
        Cart saved = cartRepository.save(cart);
        return mapToResponse(saved);
    }

    /**
     * Empty the entire cart (used after a successful checkout, or an explicit "clear cart").
     */
    @Transactional
    public CartResponse clearCart(User user) {
        Cart cart = getOrCreateCart(user);
        cart.getItems().clear();
        Cart saved = cartRepository.save(cart);
        return mapToResponse(saved);
    }

    // --- internal helpers below: not part of the public API, so no @Transactional needed on
    // these directly -- they only ever run inside a transaction already started by one of the
    // public methods above. ---

    private Cart getOrCreateCart(User user) {
        return cartRepository.findByUserId(user.getId())
                .orElseGet(() -> cartRepository.save(
                        Cart.builder()
                                .user(user)
                                .expiresAt(Instant.now().plus(CART_EXPIRY_DAYS, ChronoUnit.DAYS))
                                .build()));
    }

    /** Resets the 30-day expiry clock every time the cart is actually touched. */
    private void touchExpiry(Cart cart) {
        cart.setExpiresAt(Instant.now().plus(CART_EXPIRY_DAYS, ChronoUnit.DAYS));
    }

    private CartItem findOwnedItem(User user, UUID itemId) {
        return cartItemRepository.findByIdAndCart_User_Id(itemId, user.getId())
                .orElseThrow(() -> new AppException("Cart item not found", HttpStatus.NOT_FOUND));
    }

    /**
     * Stock check against inventory.quantity_available (on_hand - reserved). This only ever
     * READS the inventory row -- unlike checkout (a later phase), adding to a cart does not
     * reserve stock, since a cart isn't a commitment to buy. Two different people can have the
     * same low-stock item in their carts at once; the real reservation happens at checkout.
     */
    private void assertStockAvailable(ProductVariant variant, int desiredQuantity) {
        int available = inventoryRepository.findAvailableQuantityByVariantId(variant.getId()).orElse(0);
        if (desiredQuantity > available) {
            throw new AppException(
                    "Only " + available + " unit(s) of \"" + variant.getSku() + "\" left in stock",
                    HttpStatus.CONFLICT);
        }
    }

    /** Sale price wins over base price when both are set, same rule the storefront listing uses. */
    private BigDecimal effectivePrice(ProductVariant variant) {
        return variant.getSalePrice() != null ? variant.getSalePrice() : variant.getPrice();
    }

    private CartResponse mapToResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .sorted(Comparator.comparing(CartItem::getCreatedAt))
                .map(this::mapItemToResponse)
                .collect(Collectors.toList());

        BigDecimal subtotal = items.stream()
                .map(CartItemResponse::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalItems = items.stream().mapToInt(CartItemResponse::getQuantity).sum();

        return CartResponse.builder()
                .id(cart.getId())
                .items(items)
                .subtotal(subtotal)
                .totalItems(totalItems)
                .build();
    }

    private CartItemResponse mapItemToResponse(CartItem item) {
        ProductVariant variant = item.getVariant();
        Product product = variant.getProduct();
        int available = inventoryRepository.findAvailableQuantityByVariantId(variant.getId()).orElse(0);

        String primaryImage = product.getImages().stream()
                .filter(ProductImage::isPrimary)
                .map(ProductImage::getUrl)
                .findFirst()
                .orElse(product.getImages().isEmpty() ? null : product.getImages().get(0).getUrl());

        return CartItemResponse.builder()
                .id(item.getId())
                .variantId(variant.getId())
                .productId(product.getId())
                .productSlug(product.getSlug())
                .productName(product.getName())
                .variantName(variant.getName())
                .sku(variant.getSku())
                .image(primaryImage)
                .unitPrice(item.getUnitPrice())
                .quantity(item.getQuantity())
                .lineTotal(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .availableStock(available)
                .build();
    }
}
