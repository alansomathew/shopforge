package com.codewithalanso.shopforge.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentInitiateResponse {
    private UUID orderId;

    /** "razorpay" or "cod". */
    private String gateway;

    /** "INITIATED" (Razorpay -- frontend still needs to open the checkout modal) or "SUCCESS" (COD, already done). */
    private String status;

    /** Only set for gateway = "razorpay"; null for COD. */
    private String razorpayOrderId;

    /**
     * Razorpay's public key ID -- safe to hand to the frontend (checkout.js needs it to open the
     * modal). This is deliberately NOT the key secret, which never leaves the backend.
     */
    private String razorpayKeyId;

    private BigDecimal amount;
    private String currency;
}
