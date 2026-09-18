package com.codewithalanso.shopforge.services;

import com.codewithalanso.shopforge.auth.dto.CategoryNodeDto;
import com.codewithalanso.shopforge.common.exception.AppException;
import com.codewithalanso.shopforge.dto.category.CategoryRequest;
import com.codewithalanso.shopforge.dto.category.CategoryResponse;
import com.codewithalanso.shopforge.entities.Category;
import com.codewithalanso.shopforge.repositories.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    /**
     * Every category, active or not -- the admin management screen needs to see deactivated
     * ones too (to re-activate them), unlike getActiveCategoryTree() below which the public
     * storefront uses and which only ever returns active rows.
     */
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAll().stream()
                .sorted(Comparator.comparingInt(Category::getSortOrder))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CategoryResponse createCategory(CategoryRequest request) {
        Category parent = resolveParent(request.getParentId(), null);

        Category category = Category.builder()
                .parent(parent)
                .name(request.getName())
                .slug(generateUniqueSlug(request.getName()))
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .build();

        return mapToResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse updateCategory(UUID id, CategoryRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException("Category not found", HttpStatus.NOT_FOUND));

        category.setParent(resolveParent(request.getParentId(), id));
        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setImageUrl(request.getImageUrl());
        if (request.getSortOrder() != null) {
            category.setSortOrder(request.getSortOrder());
        }

        return mapToResponse(categoryRepository.save(category));
    }

    /**
     * "Delete" here means deactivate, not a real SQL DELETE. products.category_id is declared
     * ON DELETE RESTRICT (see V1__init.sql) specifically so a category can never be removed out
     * from under products that still reference it -- an actual DELETE would just fail with a
     * foreign key violation the moment any product (past or present) pointed at this row.
     * is_active already exists for exactly this "hide without breaking references" purpose --
     * getActiveCategoryTree() and the product-listing filters already respect it.
     */
    @Transactional
    public void deactivateCategory(UUID id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException("Category not found", HttpStatus.NOT_FOUND));
        category.setActive(false);
        categoryRepository.save(category);
    }

    private Category resolveParent(UUID parentId, UUID selfId) {
        if (parentId == null) {
            return null;
        }
        if (parentId.equals(selfId)) {
            throw new AppException("A category cannot be its own parent", HttpStatus.BAD_REQUEST);
        }
        return categoryRepository.findById(parentId)
                .orElseThrow(() -> new AppException("Parent category not found", HttpStatus.NOT_FOUND));
    }

    /** Same slugify-and-dedupe approach as ProductService.saveProduct -- see it for the reasoning. */
    private String generateUniqueSlug(String name) {
        String baseSlug = name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
        String slug = baseSlug;
        int suffix = 1;
        while (categoryRepository.findBySlug(slug).isPresent()) {
            slug = baseSlug + "-" + (suffix++);
        }
        return slug;
    }

    private CategoryResponse mapToResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .parentId(category.getParent() != null ? category.getParent().getId() : null)
                .name(category.getName())
                .slug(category.getSlug())
                .description(category.getDescription())
                .imageUrl(category.getImageUrl())
                .sortOrder(category.getSortOrder())
                .isActive(category.isActive())
                .build();
    }

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
