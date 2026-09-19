package com.codewithalanso.shopforge.dto.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class InitiatePaymentRequest {

    @NotNull(message = "orderId is required")
    private UUID orderId;

    /** "RAZORPAY" or "COD" -- see PaymentService.initiate for how each is handled. */
    @NotBlank(message = "method is required")
    private String method;
}
