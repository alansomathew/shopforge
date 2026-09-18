package com.codewithalanso.shopforge.services;

import com.codewithalanso.shopforge.auth.dto.CategoryNodeDto;
import com.codewithalanso.shopforge.entities.Category;
import com.codewithalanso.shopforge.repositories.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /**
     * Builds and returns a recursive hierarchy of categories using a single recursive CTE database query.
     */
    public List<CategoryNodeDto> getActiveCategoryTree() {
        List<Category> flatCategories = categoryRepository.findActiveCategoryTreeFlat();

        // Convert categories to DTOs
        Map<UUID, CategoryNodeDto> dtoMap = new LinkedHashMap<>();
        for (Category category : flatCategories) {
            CategoryNodeDto node = CategoryNodeDto.builder()
                    .id(category.getId())
                    .parentId(category.getParent() != null ? category.getParent().getId() : null)
                    .name(category.getName())
                    .slug(category.getSlug())
                    .description(category.getDescription())
                    .imageUrl(category.getImageUrl())
                    .sortOrder(category.getSortOrder())
                    .children(new ArrayList<>())
                    .build();
            dtoMap.put(category.getId(), node);
        }

        List<CategoryNodeDto> rootNodes = new ArrayList<>();

        // Build tree in-memory
        for (CategoryNodeDto node : dtoMap.values()) {
            if (node.getParentId() == null) {
                rootNodes.add(node);
            } else {
                CategoryNodeDto parentNode = dtoMap.get(node.getParentId());
                if (parentNode != null) {
                    parentNode.getChildren().add(node);
                } else {
                    // Fallback if parent not in the active flat tree
                    rootNodes.add(node);
                }
            }
        }

        // Sort children lists recursively
        for (CategoryNodeDto root : rootNodes) {
            sortNodeChildren(root);
        }

        // Sort root level elements
        rootNodes.sort(Comparator.comparingInt(CategoryNodeDto::getSortOrder));

        return rootNodes;
    }

    private void sortNodeChildren(CategoryNodeDto node) {
        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            node.getChildren().sort(Comparator.comparingInt(CategoryNodeDto::getSortOrder));
            for (CategoryNodeDto child : node.getChildren()) {
                sortNodeChildren(child);
            }
        }
    }

    /**
     * Finds a category by its slug.
     */
    public Optional<Category> getCategoryBySlug(String slug) {
        return categoryRepository.findBySlug(slug);
    }

    /**
     * Helper to return all descendant category IDs for a given parent category ID (inclusive).
     */
    public Set<UUID> getDescendantCategoryIds(UUID parentId) {
        Set<UUID> ids = new HashSet<>();
        if (parentId == null) return ids;
        ids.add(parentId);
        
        List<Category> allCategories = categoryRepository.findAll();
        Map<UUID, List<UUID>> childrenMap = allCategories.stream()
                .filter(c -> c.getParent() != null && c.isActive())
                .collect(Collectors.groupingBy(
                        c -> c.getParent().getId(),
                        Collectors.mapping(Category::getId, Collectors.toList())
                ));

        Queue<UUID> queue = new LinkedList<>();
        queue.add(parentId);
        
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            List<UUID> children = childrenMap.get(current);
            if (children != null) {
                for (UUID childId : children) {
                    if (ids.add(childId)) {
                        queue.add(childId);
                    }
                }
            }
        }
        
        return ids;
    }
}
