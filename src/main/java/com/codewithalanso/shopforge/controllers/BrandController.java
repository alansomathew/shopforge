package com.codewithalanso.shopforge.controllers;

import com.codewithalanso.shopforge.common.response.ApiResponse;
import com.codewithalanso.shopforge.dto.brand.BrandRequest;
import com.codewithalanso.shopforge.dto.brand.BrandResponse;
import com.codewithalanso.shopforge.services.BrandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Brand listing is public (SecurityConfig permits GET /api/v1/brands) -- it's the same kind of
 * catalog metadata as the category tree, useful for a brand picker on the storefront later, not
 * anything sensitive. Every mutation stays ADMIN-only.
 */
@RestController
@RequestMapping("/api/v1/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BrandResponse>>> getAllBrands() {
        List<BrandResponse> brands = brandService.getAllBrands();
        return ResponseEntity.ok(ApiResponse.success("Brands retrieved successfully", brands));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BrandResponse>> createBrand(@Valid @RequestBody BrandRequest request) {
        BrandResponse brand = brandService.createBrand(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Brand created successfully", brand));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<BrandResponse>> updateBrand(
            @PathVariable UUID id, @Valid @RequestBody BrandRequest request) {
        BrandResponse brand = brandService.updateBrand(id, request);
        return ResponseEntity.ok(ApiResponse.success("Brand updated successfully", brand));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivateBrand(@PathVariable UUID id) {
        brandService.deactivateBrand(id);
        return ResponseEntity.ok(ApiResponse.success("Brand deactivated successfully"));
    }
}
