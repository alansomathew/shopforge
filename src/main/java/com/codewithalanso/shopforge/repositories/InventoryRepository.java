package com.codewithalanso.shopforge.repositories;

import com.codewithalanso.shopforge.entities.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {
    Optional<Inventory> findByVariantId(UUID variantId);

    @Query("SELECT i.quantityOnHand - i.quantityReserved FROM Inventory i WHERE i.variant.id = :variantId")
    Optional<Integer> findAvailableQuantityByVariantId(@Param("variantId") UUID variantId);
}
