package com.codewithalanso.shopforge.dto.payment;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * What Razorpay's checkout.js hands back to the frontend in its success callback, forwarded
 * here so the backend can verify razorpay_signature itself using the (server-only) key secret.
 * Never trust that a payment succeeded just because the frontend says so -- the signature is
 * what actually proves Razorpay generated this response, not something the client could forge.
 */
@Data
public class VerifyPaymentRequest {

    @NotBlank(message = "razorpayOrderId is required")
    private String razorpayOrderId;

    @NotBlank(message = "razorpayPaymentId is required")
    private String razorpayPaymentId;

    @NotBlank(message = "razorpaySignature is required")
    private String razorpaySignature;
}
