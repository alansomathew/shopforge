package com.codewithalanso.shopforge.controllers;

import com.codewithalanso.shopforge.auth.dto.CategoryNodeDto;
import com.codewithalanso.shopforge.common.response.ApiResponse;
import com.codewithalanso.shopforge.dto.category.CategoryRequest;
import com.codewithalanso.shopforge.dto.category.CategoryResponse;
import com.codewithalanso.shopforge.services.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * Get recursive active category tree. Public -- this is what the storefront's navigation
     * and category pages use.
     */
    @GetMapping("/tree")
    public ResponseEntity<ApiResponse<List<CategoryNodeDto>>> getCategoryTree() {
        List<CategoryNodeDto> tree = categoryService.getActiveCategoryTree();
        return ResponseEntity.ok(ApiResponse.success("Category tree retrieved successfully", tree));
    }

    /**
     * Admin-only flat list of every category, active or not -- SecurityConfig only permitAll's
     * GET /api/v1/categories/tree specifically (not this whole path), so this one falls under
     * the default "must be authenticated" rule, and @PreAuthorize narrows it to ADMIN.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getAllCategories() {
        List<CategoryResponse> categories = categoryService.getAllCategories();
        return ResponseEntity.ok(ApiResponse.success("Categories retrieved successfully", categories));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse category = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Category created successfully", category));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> updateCategory(
            @PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        CategoryResponse category = categoryService.updateCategory(id, request);
        return ResponseEntity.ok(ApiResponse.success("Category updated successfully", category));
    }

    /** Deactivates the category (is_active = false) -- see CategoryService.deactivateCategory for why. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateCategory(@PathVariable UUID id) {
        categoryService.deactivateCategory(id);
        return ResponseEntity.ok(ApiResponse.success("Category deactivated successfully"));
    }
}
