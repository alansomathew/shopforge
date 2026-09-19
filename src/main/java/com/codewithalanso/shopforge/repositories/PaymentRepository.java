package com.codewithalanso.shopforge.repositories;

import com.codewithalanso.shopforge.entities.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * No RefundRepository alongside this one (yet): nothing in this phase creates or reads Refund
 * rows -- that's admin refund processing, a later phase's concern. Add it when that feature
 * actually needs it, same reasoning as OrderRepository's comment about OrderItemRepository.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByOrderId(UUID orderId);

    Optional<Payment> findByGatewayOrderId(String gatewayOrderId);
}
