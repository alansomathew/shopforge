package com.codewithalanso.shopforge.services;

import com.codewithalanso.shopforge.common.exception.AppException;
import com.codewithalanso.shopforge.dto.order.OrderItemResponse;
import com.codewithalanso.shopforge.dto.order.OrderListResponseDto;
import com.codewithalanso.shopforge.dto.order.OrderResponse;
import com.codewithalanso.shopforge.entities.Order;
import com.codewithalanso.shopforge.entities.OrderItem;
import com.codewithalanso.shopforge.entities.OrderStatus;
import com.codewithalanso.shopforge.entities.OrderStatusHistory;
import com.codewithalanso.shopforge.entities.User;
import com.codewithalanso.shopforge.repositories.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read/query side of orders, plus admin status transitions. Order *creation* lives in
 * CheckoutService instead -- that's a different, much heavier kind of operation (touches the
 * cart, inventory, and a brand-new Order all in one transaction), so it gets its own class
 * rather than being one more method bolted onto this one.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;

    /**
     * Which OrderStatus a value is allowed to move to next -- this is the state diagram from
     * the SRS, encoded as data instead of as a maze of if/else. Any status that's NOT a key
     * here (COMPLETED, RETURN_REJECTED, REFUNDED) is a terminal state: nothing can transition
     * out of it, so updateStatus() below will reject every attempt with an empty allowed-set.
     * Checking this map before every write is what stops an order from being pushed into a
     * nonsensical state, like jumping straight from PENDING to DELIVERED.
     */
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.PENDING, Set.of(OrderStatus.PAYMENT_RECEIVED, OrderStatus.CANCELLED),
            OrderStatus.PAYMENT_RECEIVED, Set.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED),
            OrderStatus.PROCESSING, Set.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED),
            OrderStatus.SHIPPED, Set.of(OrderStatus.DELIVERED, OrderStatus.RETURN_REQUESTED),
            OrderStatus.DELIVERED, Set.of(OrderStatus.RETURN_REQUESTED, OrderStatus.COMPLETED),
            OrderStatus.CANCELLED, Set.of(OrderStatus.REFUND_INITIATED),
            OrderStatus.RETURN_REQUESTED, Set.of(OrderStatus.RETURN_APPROVED, OrderStatus.RETURN_REJECTED),
            OrderStatus.REFUND_INITIATED, Set.of(OrderStatus.REFUNDED)
    );

    @Transactional(readOnly = true)
    public OrderListResponseDto getOrdersForUser(User user, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orders = orderRepository.findByUserId(user.getId(), pageable);
        return mapToPageResponse(orders);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrderForCustomer(User user, UUID orderId) {
        Order order = orderRepository.findByIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new AppException("Order not found", HttpStatus.NOT_FOUND));
        return mapToOrderResponse(order);
    }

    /**
     * Admin-only (enforced by @PreAuthorize on the controller, not here) -- so this looks the
     * order up by ID alone, with no ownership check, unlike getOrderForCustomer above.
     */
    @Transactional
    public OrderResponse updateStatus(UUID orderId, OrderStatus newStatus, User admin, String note) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException("Order not found", HttpStatus.NOT_FOUND));

        Set<OrderStatus> allowedNext = ALLOWED_TRANSITIONS.getOrDefault(order.getStatus(), Set.of());
        if (!allowedNext.contains(newStatus)) {
            throw new AppException(
                    "Cannot move an order from " + order.getStatus() + " to " + newStatus,
                    HttpStatus.CONFLICT);
        }

        OrderStatus previousStatus = order.getStatus();
        order.setStatus(newStatus);
        // Same aggregate pattern as Cart.items: appending to the parent's managed collection and
        // saving the parent (order) is enough for Hibernate to insert this history row too.
        order.getStatusHistory().add(OrderStatusHistory.builder()
                .order(order)
                .fromStatus(previousStatus)
                .toStatus(newStatus)
                .changedBy(admin)
                .note(note)
                .build());

        Order saved = orderRepository.save(order);
        return mapToOrderResponse(saved);
    }

    /**
     * Not private: CheckoutService builds a brand-new Order and needs this exact same
     * entity-to-response translation for its own return value. Rather than duplicating the
     * mapping logic in two places, checkout just calls this once it has saved the order.
     */
    public OrderResponse mapToOrderResponse(Order order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(this::mapItemToResponse)
                .collect(Collectors.toList());

        return OrderResponse.builder()
                .id(order.getId())
                .orderNumber(order.getOrderNumber())
                .status(order.getStatus())
                .shippingAddress(mapAddressSnapshot(order.getShippingAddressSnapshot()))
                .shippingMethod(order.getShippingMethod())
                .shippingCost(order.getShippingCost())
                .subtotal(order.getSubtotal())
                .discountAmount(order.getDiscountAmount())
                .taxAmount(order.getTaxAmount())
                .totalAmount(order.getTotalAmount())
                .notes(order.getNotes())
                .createdAt(order.getCreatedAt())
                .items(items)
                .build();
    }

    private OrderItemResponse mapItemToResponse(OrderItem item) {
        Map<String, Object> snapshot = item.getProductSnapshot();
        return OrderItemResponse.builder()
                .id(item.getId())
                // .getId() on a lazy @ManyToOne never triggers a DB fetch -- Hibernate's proxy
                // already knows its own ID without loading the rest of the row.
                .variantId(item.getVariant().getId())
                .productName((String) snapshot.get("productName"))
                .productSlug((String) snapshot.get("productSlug"))
                .variantName((String) snapshot.get("variantName"))
                .sku((String) snapshot.get("sku"))
                .image((String) snapshot.get("image"))
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .totalPrice(item.getTotalPrice())
                .fulfillmentStatus(item.getFulfillmentStatus())
                .build();
    }

    private OrderResponse.ShippingAddress mapAddressSnapshot(Map<String, Object> snapshot) {
        return OrderResponse.ShippingAddress.builder()
                .firstName((String) snapshot.get("firstName"))
                .lastName((String) snapshot.get("lastName"))
                .phone((String) snapshot.get("phone"))
                .addressLine1((String) snapshot.get("addressLine1"))
                .addressLine2((String) snapshot.get("addressLine2"))
                .city((String) snapshot.get("city"))
                .state((String) snapshot.get("state"))
                .country((String) snapshot.get("country"))
                .postalCode((String) snapshot.get("postalCode"))
                .build();
    }

    private OrderListResponseDto mapToPageResponse(Page<Order> page) {
        List<OrderResponse> content = page.getContent().stream()
                .map(this::mapToOrderResponse)
                .collect(Collectors.toList());

        return OrderListResponseDto.builder()
                .content(content)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}
