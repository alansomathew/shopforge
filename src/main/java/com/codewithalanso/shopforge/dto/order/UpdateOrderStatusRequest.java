package com.codewithalanso.shopforge.dto.order;

import com.codewithalanso.shopforge.entities.OrderStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Body for the admin-only PATCH /api/v1/orders/{id}/status endpoint. Jackson deserializes the
 * incoming JSON string straight into the OrderStatus enum by matching it against the constant
 * names (e.g. "SHIPPED" -> OrderStatus.SHIPPED) -- no manual parsing needed. If the client sends
 * a string that doesn't match any constant, Jackson fails before this even reaches the
 * controller, which GlobalExceptionHandler's catch-all turns into a 400-ish error response.
 */
@Data
public class UpdateOrderStatusRequest {

    @NotNull(message = "status is required")
    private OrderStatus status;

    /** Optional admin note explaining the change, stored on the OrderStatusHistory row. */
    private String note;
}
