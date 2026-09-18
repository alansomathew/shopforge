package com.codewithalanso.shopforge.controllers;

import com.codewithalanso.shopforge.auth.security.CustomUserDetails;
import com.codewithalanso.shopforge.common.response.ApiResponse;
import com.codewithalanso.shopforge.dto.order.CheckoutRequest;
import com.codewithalanso.shopforge.dto.order.OrderListResponseDto;
import com.codewithalanso.shopforge.dto.order.OrderResponse;
import com.codewithalanso.shopforge.dto.order.UpdateOrderStatusRequest;
import com.codewithalanso.shopforge.entities.User;
import com.codewithalanso.shopforge.services.CheckoutService;
import com.codewithalanso.shopforge.services.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final CheckoutService checkoutService;
    private final OrderService orderService;

    /**
     * Place an order from the caller's current cart. Any authenticated user qualifies -- same
     * "no @PreAuthorize needed" reasoning as CartController, since every logged-in user should
     * be able to buy something.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> checkout(
            @Valid @RequestBody CheckoutRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userDetails.getUser();
        OrderResponse order = checkoutService.checkout(user, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Order placed successfully", order));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<OrderListResponseDto>> getMyOrders(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        User user = userDetails.getUser();
        OrderListResponseDto orders = orderService.getOrdersForUser(user, page, size);
        return ResponseEntity.ok(ApiResponse.success("Orders retrieved successfully", orders));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getMyOrder(
            @PathVariable UUID id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userDetails.getUser();
        OrderResponse order = orderService.getOrderForCustomer(user, id);
        return ResponseEntity.ok(ApiResponse.success("Order retrieved successfully", order));
    }

    /**
     * Admin-only order fulfillment transition (PENDING -> PAYMENT_RECEIVED -> PROCESSING ->
     * SHIPPED -> ...). See OrderService.ALLOWED_TRANSITIONS for exactly which moves are legal.
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateOrderStatusRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User admin = userDetails.getUser();
        OrderResponse order = orderService.updateStatus(id, request.getStatus(), admin, request.getNote());
        return ResponseEntity.ok(ApiResponse.success("Order status updated successfully", order));
    }
}
