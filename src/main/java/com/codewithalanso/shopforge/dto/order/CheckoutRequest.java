package com.codewithalanso.shopforge.dto.order;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Everything the client needs to send to place an order: which saved address to ship to, and
 * that's essentially it. Notice there's no price, subtotal, or total field here at all -- the
 * server (CheckoutService) recomputes every number itself from the cart and current product
 * prices. Never accept a price or total from the client; if this DTO had a `totalAmount` field,
 * a modified request could just claim the order costs ₹1.
 */
@Data
public class CheckoutRequest {

    @NotNull(message = "addressId is required")
    private UUID addressId;

    /** Optional; defaults to "STANDARD" in CheckoutService if omitted. */
    private String shippingMethod;

    /** Optional delivery instructions from the customer. */
    private String notes;
}
