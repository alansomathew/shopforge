package com.codewithalanso.shopforge.dto.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One line of an order response. Built from OrderItem.productSnapshot (the frozen JSONB copy
 * of product/variant details taken at checkout) rather than by joining to the live Product --
 * if the seller renames the product or changes its images next week, an order placed today
 * must still show what the customer actually bought, not today's catalog state.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemResponse {
    private UUID id;
    private UUID variantId;
    private String productName;
    private String productSlug;
    private String variantName;
    private String sku;
    private String image;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal totalPrice;
    private String fulfillmentStatus;
}
