package com.codewithalanso.shopforge.entities;

public enum OrderStatus {
    PENDING,
    PAYMENT_RECEIVED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    COMPLETED,
    CANCELLED,
    RETURN_REQUESTED,
    RETURN_APPROVED,
    RETURN_REJECTED,
    REFUND_INITIATED,
    REFUNDED
}
