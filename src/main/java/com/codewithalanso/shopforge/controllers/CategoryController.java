package com.codewithalanso.shopforge.controllers;

import com.codewithalanso.shopforge.auth.dto.CategoryNodeDto;
import com.codewithalanso.shopforge.common.response.ApiResponse;
import com.codewithalanso.shopforge.services.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * Get recursive active category tree.
     */
    @GetMapping("/tree")
    public ResponseEntity<ApiResponse<List<CategoryNodeDto>>> getCategoryTree() {
        List<CategoryNodeDto> tree = categoryService.getActiveCategoryTree();
        return ResponseEntity.ok(ApiResponse.success("Category tree retrieved successfully", tree));
    }
}
