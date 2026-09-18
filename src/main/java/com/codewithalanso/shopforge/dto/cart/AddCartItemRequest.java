package com.codewithalanso.shopforge.dto.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * Request body for "add this variant to my cart" (POST /api/v1/cart/items).
 *
 * This is a plain data holder -- it exists purely to describe the shape of JSON the client
 * sends, so it's a separate class from the CartItem *entity*. That separation matters: the
 * entity has fields like unitPrice and cart/variant object references that the client should
 * never be trusted to send directly (imagine a client sending its own "unitPrice": 0.01!). The
 * controller only ever accepts THIS shape, and the service decides what to actually do with it
 * -- including looking up the real current price itself.
 *
 * The jakarta.validation annotations (@NotNull, @Min, @Max) are declarative rules Spring checks
 * automatically before your controller method body even runs, as long as the parameter is
 * annotated with @Valid (see CartController). If a rule fails, Spring throws
 * MethodArgumentNotValidException, which GlobalExceptionHandler turns into a 400 response
 * listing exactly which field failed -- you never have to write that "if" check by hand.
 */
@Data
public class AddCartItemRequest {

    @NotNull(message = "variantId is required")
    private UUID variantId;

    @NotNull(message = "quantity is required")
    @Min(value = 1, message = "quantity must be at least 1")
    @Max(value = 99, message = "quantity cannot exceed 99")
    private Integer quantity;
}
