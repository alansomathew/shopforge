package com.codewithalanso.shopforge.repositories;

import com.codewithalanso.shopforge.entities.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {
    
    Optional<Category> findBySlug(String slug);
    
    List<Category> findByParentIsNullAndIsActiveTrueOrderBySortOrderAsc();

    @Query(value = "WITH RECURSIVE cat_tree AS (" +
            "  SELECT id, parent_id, name, slug, description, image_url, sort_order, is_active, meta_title, meta_description, created_at, updated_at, 0 as depth " +
            "  FROM categories WHERE parent_id IS NULL AND is_active = true " +
            "  UNION ALL " +
            "  SELECT c.id, c.parent_id, c.name, c.slug, c.description, c.image_url, c.sort_order, c.is_active, c.meta_title, c.meta_description, c.created_at, c.updated_at, ct.depth + 1 " +
            "  FROM categories c JOIN cat_tree ct ON c.parent_id = ct.id " +
            "  WHERE c.is_active = true " +
            ") " +
            "SELECT id, parent_id, name, slug, description, image_url, sort_order, is_active, meta_title, meta_description, created_at, updated_at " +
            "FROM cat_tree " +
            "ORDER BY depth, sort_order", nativeQuery = true)
    List<Category> findActiveCategoryTreeFlat();
}
