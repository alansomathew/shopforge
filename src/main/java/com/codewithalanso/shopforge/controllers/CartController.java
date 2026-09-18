package com.codewithalanso.shopforge.controllers;

import com.codewithalanso.shopforge.auth.security.CustomUserDetails;
import com.codewithalanso.shopforge.common.response.ApiResponse;
import com.codewithalanso.shopforge.dto.cart.AddCartItemRequest;
import com.codewithalanso.shopforge.dto.cart.CartResponse;
import com.codewithalanso.shopforge.dto.cart.UpdateCartItemRequest;
import com.codewithalanso.shopforge.entities.User;
import com.codewithalanso.shopforge.services.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Every endpoint here requires the caller to be logged in, but notice there's no
 * @PreAuthorize("hasRole(...)") anywhere -- unlike, say, ProductController's create/update/
 * delete endpoints. That's because SecurityConfig's chain ends with `.anyRequest()
 * .authenticated()`, and /api/v1/cart isn't listed anywhere as permitAll(). So "any logged-in
 * user, regardless of role" already satisfies that default rule -- exactly what a shopping
 * cart needs, since every CUSTOMER (and SELLER, and ADMIN) should be able to have one.
 *
 * @AuthenticationPrincipal CustomUserDetails userDetails is how a controller method gets hold
 * of "who is making this request" -- JwtFilter (which runs on every request before this
 * controller method) already validated the JWT and loaded the matching CustomUserDetails into
 * Spring Security's SecurityContext; this annotation just hands you that same object without
 * you needing to touch SecurityContextHolder yourself.
 */
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userDetails.getUser();
        CartResponse cart = cartService.getCart(user);
        return ResponseEntity.ok(ApiResponse.success("Cart retrieved successfully", cart));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @Valid @RequestBody AddCartItemRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userDetails.getUser();
        CartResponse cart = cartService.addItem(user, request);
        return ResponseEntity.ok(ApiResponse.success("Item added to cart", cart));
    }

    @PatchMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateItem(
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateCartItemRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userDetails.getUser();
        CartResponse cart = cartService.updateItemQuantity(user, itemId, request);
        return ResponseEntity.ok(ApiResponse.success("Cart item updated", cart));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @PathVariable UUID itemId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userDetails.getUser();
        CartResponse cart = cartService.removeItem(user, itemId);
        return ResponseEntity.ok(ApiResponse.success("Item removed from cart", cart));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<CartResponse>> clearCart(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userDetails.getUser();
        CartResponse cart = cartService.clearCart(user);
        return ResponseEntity.ok(ApiResponse.success("Cart cleared", cart));
    }
}
