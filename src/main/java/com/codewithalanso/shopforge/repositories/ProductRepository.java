package com.codewithalanso.shopforge.repositories;

import com.codewithalanso.shopforge.entities.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySlugAndStatus(String slug, com.codewithalanso.shopforge.entities.ProductStatus status);

    @Query(value = "SELECT p.* FROM products p " +
            "WHERE p.search_vector @@ plainto_tsquery('english', :query) " +
            "AND p.status = 'ACTIVE' AND p.deleted_at IS NULL " +
            "ORDER BY ts_rank(p.search_vector, plainto_tsquery('english', :query)) DESC",
            countQuery = "SELECT count(*) FROM products p WHERE p.search_vector @@ plainto_tsquery('english', :query) AND p.status = 'ACTIVE' AND p.deleted_at IS NULL",
            nativeQuery = true)
    Page<Product> searchProducts(@Param("query") String query, Pageable pageable);
}
