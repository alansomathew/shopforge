package com.codewithalanso.shopforge.repositories;

import com.codewithalanso.shopforge.entities.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Note there's no separate OrderItemRepository or OrderStatusHistoryRepository here, even
 * though the roadmap originally sketched them: Order.items and Order.statusHistory both use
 * cascade = ALL + orphanRemoval = true (see the Order entity), the same aggregate-root pattern
 * as Cart/CartItem -- items and history rows are only ever created or read through their parent
 * Order, so a standalone repository for either would currently have zero callers. If a later
 * phase needs to query order_items directly (e.g. "has this user bought this product" for
 * verified-purchase reviews), that's the point to add it -- not before it's actually needed.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    Page<Order> findByUserId(UUID userId, Pageable pageable);

    Optional<Order> findByIdAndUserId(UUID id, UUID userId);
}
