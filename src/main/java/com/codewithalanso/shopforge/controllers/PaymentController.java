package com.codewithalanso.shopforge.controllers;

import com.codewithalanso.shopforge.auth.security.CustomUserDetails;
import com.codewithalanso.shopforge.common.response.ApiResponse;
import com.codewithalanso.shopforge.dto.order.OrderResponse;
import com.codewithalanso.shopforge.dto.payment.InitiatePaymentRequest;
import com.codewithalanso.shopforge.dto.payment.PaymentInitiateResponse;
import com.codewithalanso.shopforge.dto.payment.VerifyPaymentRequest;
import com.codewithalanso.shopforge.entities.User;
import com.codewithalanso.shopforge.services.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    public ResponseEntity<ApiResponse<PaymentInitiateResponse>> initiate(
            @Valid @RequestBody InitiatePaymentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userDetails.getUser();
        PaymentInitiateResponse response = paymentService.initiate(user, request.getOrderId(), request.getMethod());
        return ResponseEntity.ok(ApiResponse.success("Payment initiated", response));
    }

    /** Synchronous confirmation from checkout.js's success handler -- see PaymentService's class comment for why this exists alongside the webhook. */
    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<OrderResponse>> verify(
            @Valid @RequestBody VerifyPaymentRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userDetails.getUser();
        OrderResponse order = paymentService.verify(user, request);
        return ResponseEntity.ok(ApiResponse.success("Payment verified successfully", order));
    }

    /**
     * Public (see SecurityConfig) -- Razorpay's own servers call this directly, with no user
     * session at all. Authenticity comes entirely from the HMAC signature in the header, not
     * from being logged in, which is exactly what PaymentService.handleWebhook checks first.
     */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestBody String payload,
            @RequestHeader("X-Razorpay-Signature") String signature) {
        paymentService.handleWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }
}
