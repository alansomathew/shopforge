package com.codewithalanso.shopforge.services;

import com.codewithalanso.shopforge.auth.service.EmailService;
import com.codewithalanso.shopforge.common.exception.AppException;
import com.codewithalanso.shopforge.dto.order.CheckoutRequest;
import com.codewithalanso.shopforge.dto.order.OrderResponse;
import com.codewithalanso.shopforge.entities.Address;
import com.codewithalanso.shopforge.entities.Cart;
import com.codewithalanso.shopforge.entities.CartItem;
import com.codewithalanso.shopforge.entities.InventoryTransaction;
import com.codewithalanso.shopforge.entities.InventoryTransactionType;
import com.codewithalanso.shopforge.entities.Order;
import com.codewithalanso.shopforge.entities.OrderItem;
import com.codewithalanso.shopforge.entities.OrderStatus;
import com.codewithalanso.shopforge.entities.OrderStatusHistory;
import com.codewithalanso.shopforge.entities.Product;
import com.codewithalanso.shopforge.entities.ProductImage;
import com.codewithalanso.shopforge.entities.ProductVariant;
import com.codewithalanso.shopforge.entities.User;
import com.codewithalanso.shopforge.repositories.AddressRepository;
import com.codewithalanso.shopforge.repositories.CartRepository;
import com.codewithalanso.shopforge.repositories.InventoryRepository;
import com.codewithalanso.shopforge.repositories.InventoryTransactionRepository;
import com.codewithalanso.shopforge.repositories.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a cart into a real Order. This is the one operation in the whole checkout flow where
 * getting it wrong costs actual money, so every number below is computed fresh from
 * ProductVariant/Inventory right here on the server -- CheckoutRequest (see that class) doesn't
 * even have a price field to accidentally trust.
 */
@Service
@RequiredArgsConstructor
public class CheckoutService {

    private static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("499.00");
    private static final BigDecimal STANDARD_SHIPPING_FEE = new BigDecimal("99.00");

    private final CartService cartService;
    private final CartRepository cartRepository;
    private final AddressRepository addressRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final EmailService emailService;

    @Transactional
    public OrderResponse checkout(User user, CheckoutRequest request) {
        Cart cart = cartService.getCartForCheckout(user);

        Address address = addressRepository.findByIdAndUser(request.getAddressId(), user)
                .orElseThrow(() -> new AppException("Address not found", HttpStatus.NOT_FOUND));

        // Re-check stock and re-price every line against CURRENT product data right now --
        // never whatever was cached in the cart, and never anything the client sent. Prices (or
        // stock) may have changed in the time between "added to cart" and "clicked checkout".
        List<OrderItem> orderItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;

        for (CartItem cartItem : cart.getItems()) {
            ProductVariant variant = cartItem.getVariant();
            int quantity = cartItem.getQuantity();

            int available = inventoryRepository.findAvailableQuantityByVariantId(variant.getId()).orElse(0);
            if (quantity > available) {
                throw new AppException(
                        "Only " + available + " unit(s) of \"" + variant.getSku() + "\" left in stock",
                        HttpStatus.CONFLICT);
            }

            BigDecimal unitPrice = variant.getSalePrice() != null ? variant.getSalePrice() : variant.getPrice();
            BigDecimal totalPrice = unitPrice.multiply(BigDecimal.valueOf(quantity));
            subtotal = subtotal.add(totalPrice);

            Product product = variant.getProduct();
            orderItems.add(OrderItem.builder()
                    .variant(variant)
                    .productSnapshot(buildProductSnapshot(product, variant))
                    .quantity(quantity)
                    .unitPrice(unitPrice)
                    .totalPrice(totalPrice)
                    .seller(product.getSeller())
                    .build());
        }

        BigDecimal shippingCost = subtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0
                ? BigDecimal.ZERO
                : STANDARD_SHIPPING_FEE;
        // Honest zeros, not bugs: no coupon engine yet (roadmap Phase 5) and no tax engine at
        // all exists in this codebase today. Both get wired in once those features exist.
        BigDecimal discountAmount = BigDecimal.ZERO;
        BigDecimal taxAmount = BigDecimal.ZERO;
        BigDecimal totalAmount = subtotal.add(shippingCost).add(taxAmount).subtract(discountAmount);

        Order order = Order.builder()
                .user(user)
                .shippingAddress(address)
                .shippingAddressSnapshot(buildAddressSnapshot(address))
                .shippingMethod(request.getShippingMethod() != null ? request.getShippingMethod() : "STANDARD")
                .shippingCost(shippingCost)
                .subtotal(subtotal)
                .discountAmount(discountAmount)
                .taxAmount(taxAmount)
                .totalAmount(totalAmount)
                .notes(request.getNotes())
                .build();

        // Same aggregate-root cascading as Cart/CartItem: wiring the child objects onto `order`
        // and saving `order` once is enough. Order.items and Order.statusHistory both have
        // cascade = CascadeType.ALL, orphanRemoval = true, so this single save() inserts the
        // orders row, then the order_items rows, then the order_status_history row -- all in
        // this one transaction, in the right dependency order.
        for (OrderItem item : orderItems) {
            item.setOrder(order);
        }
        order.setItems(orderItems);
        order.getStatusHistory().add(OrderStatusHistory.builder()
                .order(order)
                .toStatus(OrderStatus.PENDING)
                .changedBy(user)
                .note("Order placed")
                .build());

        Order savedOrder = orderRepository.save(order);

        // Reserve stock for every line: a RESERVATION-type transaction makes the
        // apply_inventory_delta trigger increase inventory.quantity_reserved (see
        // V1__init.sql), which reduces quantity_available WITHOUT touching quantity_on_hand.
        // The stock isn't physically gone -- nothing has shipped yet -- it's just no longer
        // available for anyone else to add to their own cart while this order exists.
        for (OrderItem item : savedOrder.getItems()) {
            inventoryTransactionRepository.save(InventoryTransaction.builder()
                    .variant(item.getVariant())
                    .type(InventoryTransactionType.RESERVATION)
                    .quantityDelta(item.getQuantity())
                    .referenceType("order")
                    .referenceId(savedOrder.getId())
                    .note("Reserved at checkout")
                    .createdBy(user)
                    .build());
        }

        cart.getItems().clear();
        cartRepository.save(cart);

        emailService.sendOrderPlacedEmail(user.getEmail(), savedOrder.getOrderNumber(), savedOrder.getTotalAmount());

        return orderService.mapToOrderResponse(savedOrder);
    }

    private Map<String, Object> buildAddressSnapshot(Address address) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("firstName", address.getFirstName());
        snapshot.put("lastName", address.getLastName());
        snapshot.put("phone", address.getPhone());
        snapshot.put("addressLine1", address.getAddressLine1());
        snapshot.put("addressLine2", address.getAddressLine2());
        snapshot.put("city", address.getCity());
        snapshot.put("state", address.getState());
        snapshot.put("country", address.getCountry());
        snapshot.put("postalCode", address.getPostalCode());
        return snapshot;
    }

    private Map<String, Object> buildProductSnapshot(Product product, ProductVariant variant) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("productId", product.getId().toString());
        snapshot.put("productName", product.getName());
        snapshot.put("productSlug", product.getSlug());
        snapshot.put("variantName", variant.getName());
        snapshot.put("sku", variant.getSku());
        String image = product.getImages().stream()
                .filter(ProductImage::isPrimary)
                .map(ProductImage::getUrl)
                .findFirst()
                .orElse(product.getImages().isEmpty() ? null : product.getImages().get(0).getUrl());
        snapshot.put("image", image);
        return snapshot;
    }
}
