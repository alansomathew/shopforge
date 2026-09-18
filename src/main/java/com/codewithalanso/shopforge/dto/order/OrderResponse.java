package com.codewithalanso.shopforge.dto.order;

import com.codewithalanso.shopforge.entities.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private UUID id;
    private String orderNumber;
    private OrderStatus status;
    private ShippingAddress shippingAddress;
    private String shippingMethod;
    private BigDecimal shippingCost;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private String notes;
    private Instant createdAt;
    private List<OrderItemResponse> items;

    /**
     * A frozen copy of the address as it was at checkout time (see Order.shippingAddressSnapshot
     * in the entity). Deliberately its own type here, separate from the live Address entity/DTO
     * used elsewhere: if the customer edits or deletes that saved address afterward, this order
     * must keep showing exactly what was true the moment they placed it.
     *
     * A nested static class like this -- one type that only ever makes sense as part of its
     * parent -- follows the same pattern as ProductSaveRequest.VariantSaveRequest elsewhere in
     * this codebase.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShippingAddress {
        private String firstName;
        private String lastName;
        private String phone;
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String state;
        private String country;
        private String postalCode;
    }
}
