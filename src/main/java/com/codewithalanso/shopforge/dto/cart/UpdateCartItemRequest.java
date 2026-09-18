package com.codewithalanso.shopforge.dto.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body for "change the quantity of an item already in my cart" (PATCH
 * /api/v1/cart/items/{itemId}). Which item is identified by the {itemId} in the URL path, not by
 * anything in this body -- so all this needs to carry is the new quantity.
 */
@Data
public class UpdateCartItemRequest {

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be at least 1")
    @Max(value = 99, message = "quantity cannot exceed 99")
    private Integer quantity;
}
