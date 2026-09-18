package com.codewithalanso.shopforge.dto.cart;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The full cart, as returned by every CartController endpoint (GET, and every mutation) so the
 * frontend can just re-render from whatever response it gets back, instead of tracking cart
 * state itself and hoping it stays in sync with the server.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartResponse {
    private UUID id;
    private List<CartItemResponse> items;

    /** Sum of every line's lineTotal -- computed once here so the frontend doesn't have to. */
    private BigDecimal subtotal;

    /** Sum of every line's quantity -- handy for a "3" badge on the cart icon. */
    private int totalItems;
}
