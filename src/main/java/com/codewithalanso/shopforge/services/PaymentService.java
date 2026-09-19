package com.codewithalanso.shopforge.services;

import com.codewithalanso.shopforge.auth.service.EmailService;
import com.codewithalanso.shopforge.common.exception.AppException;
import com.codewithalanso.shopforge.dto.order.OrderResponse;
import com.codewithalanso.shopforge.dto.payment.PaymentInitiateResponse;
import com.codewithalanso.shopforge.dto.payment.VerifyPaymentRequest;
import com.codewithalanso.shopforge.entities.Order;
import com.codewithalanso.shopforge.entities.OrderStatus;
import com.codewithalanso.shopforge.entities.Payment;
import com.codewithalanso.shopforge.entities.PaymentStatus;
import com.codewithalanso.shopforge.entities.User;
import com.codewithalanso.shopforge.repositories.OrderRepository;
import com.codewithalanso.shopforge.repositories.PaymentRepository;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Turns a PENDING order (already created by CheckoutService) into a paid one. Two gateways:
 * Razorpay (needs a round trip to Razorpay's API + the customer completing their checkout.js
 * modal) and Cash on Delivery (synchronous -- there's nothing to wait for).
 *
 * A note on the "verify" vs "webhook" split below, since the roadmap only asked for a webhook:
 * Razorpay's own documentation recommends verifying razorpay_signature synchronously in
 * checkout.js's success handler, with the webhook as an asynchronous backup/reconciliation path
 * -- not an either/or. That backup matters a lot locally: Razorpay's real servers cannot reach
 * "localhost:8080" to deliver a webhook at all without extra tunneling (ngrok or similar), so
 * without the synchronous /verify endpoint, a payment would succeed at Razorpay but this app
 * would never find out about it during local development.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    // Empty defaults on purpose: Cash on Delivery needs none of this configured at all, and
    // initiateRazorpay()/handleWebhook() below check for a blank key before ever calling out to
    // Razorpay, rather than letting a confusing NullPointerException surface instead.
    @Value("${razorpay.key-id:}")
    private String razorpayKeyId;

    @Value("${razorpay.key-secret:}")
    private String razorpayKeySecret;

    @Value("${razorpay.webhook-secret:}")
    private String razorpayWebhookSecret;

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final EmailService emailService;

    @Transactional
    public PaymentInitiateResponse initiate(User user, UUID orderId, String method) {
        Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new AppException("Order not found", HttpStatus.NOT_FOUND));

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new AppException("This order is not awaiting payment", HttpStatus.CONFLICT);
        }

        // payments.order_id is UNIQUE at the database level (one payment per order), so a retry
        // after a failed attempt must reuse and update that same row, never insert a second one.
        Payment payment = paymentRepository.findByOrderId(order.getId()).orElse(null);
        if (payment != null) {
            if (payment.getStatus() == PaymentStatus.SUCCESS) {
                throw new AppException("This order has already been paid", HttpStatus.CONFLICT);
            }
            if (payment.getStatus() == PaymentStatus.INITIATED) {
                throw new AppException("A payment is already in progress for this order", HttpStatus.CONFLICT);
            }
            // Only remaining case is FAILED -- fall through and reuse this row for the retry.
        }

        if ("COD".equalsIgnoreCase(method)) {
            return initiateCod(order, payment);
        }
        if ("RAZORPAY".equalsIgnoreCase(method)) {
            return initiateRazorpay(order, payment);
        }
        throw new AppException("Unsupported payment method: " + method, HttpStatus.BAD_REQUEST);
    }

    private PaymentInitiateResponse initiateCod(Order order, Payment existing) {
        Payment payment = existing != null ? existing : new Payment();
        payment.setOrder(order);
        payment.setGateway("cod");
        payment.setMethod("cod");
        payment.setAmount(order.getTotalAmount());
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(Instant.now());
        if (payment.getIdempotencyKey() == null) {
            payment.setIdempotencyKey(UUID.randomUUID().toString());
        }
        paymentRepository.save(payment);

        // Same aggregate state machine as any admin-driven transition (see OrderService), just
        // triggered by this payment flow instead of a human -- passing null for the "admin" who
        // changed the status is intentional: OrderStatusHistory.changedBy is nullable precisely
        // for system-triggered transitions like this one, where there genuinely is no admin.
        orderService.updateStatus(order.getId(), OrderStatus.PAYMENT_RECEIVED, null, "Cash on Delivery confirmed at checkout");
        sendPaymentConfirmedEmail(order);

        return PaymentInitiateResponse.builder()
                .orderId(order.getId())
                .gateway("cod")
                .status("SUCCESS")
                .amount(order.getTotalAmount())
                .currency("INR")
                .build();
    }

    private PaymentInitiateResponse initiateRazorpay(Order order, Payment existing) {
        if (razorpayKeyId == null || razorpayKeyId.isBlank()) {
            throw new AppException(
                    "Online payment is not configured on this server yet -- try Cash on Delivery instead",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }

        try {
            com.razorpay.RazorpayClient client = new com.razorpay.RazorpayClient(razorpayKeyId, razorpayKeySecret);

            JSONObject orderRequest = new JSONObject();
            // Razorpay wants the amount in the smallest currency unit (paise, not rupees).
            long amountInPaise = order.getTotalAmount().multiply(BigDecimal.valueOf(100)).longValueExact();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", order.getOrderNumber() != null ? order.getOrderNumber() : order.getId().toString());
            orderRequest.put("payment_capture", 1);

            com.razorpay.Order razorpayOrder = client.orders.create(orderRequest);
            String gatewayOrderId = razorpayOrder.get("id");

            Payment payment = existing != null ? existing : new Payment();
            payment.setOrder(order);
            payment.setGateway("razorpay");
            payment.setGatewayOrderId(gatewayOrderId);
            payment.setAmount(order.getTotalAmount());
            payment.setStatus(PaymentStatus.INITIATED);
            if (payment.getIdempotencyKey() == null) {
                payment.setIdempotencyKey(UUID.randomUUID().toString());
            }
            paymentRepository.save(payment);

            return PaymentInitiateResponse.builder()
                    .orderId(order.getId())
                    .gateway("razorpay")
                    .status("INITIATED")
                    .razorpayOrderId(gatewayOrderId)
                    .razorpayKeyId(razorpayKeyId)
                    .amount(order.getTotalAmount())
                    .currency("INR")
                    .build();
        } catch (RazorpayException e) {
            log.error("Failed to create Razorpay order for order {}: {}", order.getId(), e.getMessage());
            throw new AppException("Failed to initiate payment: " + e.getMessage(), HttpStatus.BAD_GATEWAY);
        }
    }

    /**
     * Called by the frontend's checkout.js success handler, immediately after Razorpay reports
     * a successful payment client-side. Recomputing and checking razorpay_signature ourselves
     * is the whole point -- the frontend telling us "it worked" proves nothing on its own; only
     * a signature produced with Razorpay's key secret (which the frontend never has) does.
     */
    @Transactional
    public OrderResponse verify(User user, VerifyPaymentRequest request) {
        Payment payment = paymentRepository.findByGatewayOrderId(request.getRazorpayOrderId())
                .orElseThrow(() -> new AppException("Payment not found", HttpStatus.NOT_FOUND));

        Order order = payment.getOrder();
        if (order.getUser() == null || !order.getUser().getId().equals(user.getId())) {
            // Same order not found response as a genuinely-missing order -- don't confirm to an
            // attacker that a given Razorpay order ID belongs to someone else's order.
            throw new AppException("Payment not found", HttpStatus.NOT_FOUND);
        }

        JSONObject options = new JSONObject();
        options.put("razorpay_order_id", request.getRazorpayOrderId());
        options.put("razorpay_payment_id", request.getRazorpayPaymentId());
        options.put("razorpay_signature", request.getRazorpaySignature());

        boolean valid;
        try {
            valid = Utils.verifyPaymentSignature(options, razorpayKeySecret);
        } catch (RazorpayException e) {
            valid = false;
        }

        if (!valid) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            throw new AppException("Payment verification failed", HttpStatus.BAD_REQUEST);
        }

        payment.setGatewayPaymentId(request.getRazorpayPaymentId());
        payment.setGatewaySignature(request.getRazorpaySignature());
        payment.setMethod("razorpay");
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setPaidAt(Instant.now());
        paymentRepository.save(payment);

        OrderResponse updated = orderService.updateStatus(
                order.getId(), OrderStatus.PAYMENT_RECEIVED, null, "Razorpay payment verified (client-side)");
        sendPaymentConfirmedEmail(order);

        return updated;
    }

    /**
     * Razorpay's own async confirmation, for when it can actually reach this server (a deployed
     * environment, not local dev without a tunnel) -- see the class-level note on why /verify
     * exists too. Idempotent by design: Razorpay retries webhook delivery on anything other than
     * a 2xx response, so a payment already in a terminal state (SUCCESS/FAILED) short-circuits
     * instead of re-processing a duplicate delivery.
     */
    @Transactional
    public void handleWebhook(String payload, String signature) {
        if (razorpayWebhookSecret == null || razorpayWebhookSecret.isBlank()) {
            log.warn("Received a Razorpay webhook but no webhook secret is configured -- ignoring.");
            return;
        }

        boolean valid;
        try {
            valid = Utils.verifyWebhookSignature(payload, signature, razorpayWebhookSecret);
        } catch (RazorpayException e) {
            valid = false;
        }
        if (!valid) {
            throw new AppException("Invalid webhook signature", HttpStatus.UNAUTHORIZED);
        }

        JSONObject event = new JSONObject(payload);
        String eventType = event.optString("event", "");
        JSONObject paymentEntity = event.getJSONObject("payload").getJSONObject("payment").getJSONObject("entity");
        String gatewayOrderId = paymentEntity.getString("order_id");
        String gatewayPaymentId = paymentEntity.getString("id");

        Payment payment = paymentRepository.findByGatewayOrderId(gatewayOrderId).orElse(null);
        if (payment == null) {
            log.warn("Webhook for unknown Razorpay order {}", gatewayOrderId);
            return;
        }
        if (payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.FAILED) {
            log.info("Payment {} already {}, ignoring duplicate webhook delivery", payment.getId(), payment.getStatus());
            return;
        }

        switch (eventType) {
            case "payment.captured" -> {
                payment.setGatewayPaymentId(gatewayPaymentId);
                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setPaidAt(Instant.now());
                paymentRepository.save(payment);
                orderService.updateStatus(
                        payment.getOrder().getId(), OrderStatus.PAYMENT_RECEIVED, null, "Payment captured (webhook)");
                sendPaymentConfirmedEmail(payment.getOrder());
            }
            case "payment.failed" -> {
                payment.setStatus(PaymentStatus.FAILED);
                paymentRepository.save(payment);
            }
            default -> log.info("Ignoring unhandled Razorpay webhook event: {}", eventType);
        }
    }

    private void sendPaymentConfirmedEmail(Order order) {
        String email = order.getUser() != null ? order.getUser().getEmail() : order.getGuestEmail();
        if (email == null) {
            return;
        }
        emailService.sendPaymentConfirmedEmail(email, order.getOrderNumber(), order.getTotalAmount());
    }
}
