package com.codewithalanso.shopforge.controllers;

import com.codewithalanso.shopforge.auth.dto.ProductDetailResponse;
import com.codewithalanso.shopforge.auth.dto.ProductListResponseDto;
import com.codewithalanso.shopforge.auth.dto.ProductSaveRequest;
import com.codewithalanso.shopforge.auth.dto.InventoryAdjustRequest;
import com.codewithalanso.shopforge.auth.security.CustomUserDetails;
import com.codewithalanso.shopforge.common.exception.AppException;
import com.codewithalanso.shopforge.common.response.ApiResponse;
import com.codewithalanso.shopforge.entities.User;
import com.codewithalanso.shopforge.services.ProductService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    /**
     * Get paginated products with dynamic searching and filtering.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<ProductListResponseDto>> getProducts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String brand,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Integer minRating,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(required = false) Boolean featured,
            @RequestParam(required = false) String sortBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        ProductListResponseDto response = productService.getProducts(
                search, category, brand, minPrice, maxPrice, minRating, inStock, featured, sortBy, page, size);
        return ResponseEntity.ok(ApiResponse.success("Products retrieved successfully", response));
    }

    /**
     * Get detailed product by its slug.
     */
    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProductBySlug(@PathVariable String slug) {
        ProductDetailResponse product = productService.getProductBySlug(slug)
                .orElseThrow(() -> new AppException("Product not found", HttpStatus.NOT_FOUND));
        return ResponseEntity.ok(ApiResponse.success("Product details retrieved successfully", product));
    }
    /**
     * Create a new product. Restricted to Admin and Seller roles.
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER')")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> createProduct(
            @Valid @RequestBody ProductSaveRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        User seller = userDetails.getUser();
        ProductDetailResponse product = productService.saveProduct(request, seller);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Product created successfully", product));
    }

    /**
     * Update an existing product. Restricted to Admin and Seller roles.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER')")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateProduct(
            @PathVariable UUID id,
            @Valid @RequestBody ProductSaveRequest request) {
        
        ProductDetailResponse product = productService.updateProduct(id, request);
        return ResponseEntity.ok(ApiResponse.success("Product updated successfully", product));
    }

    /**
     * Adjust inventory stock (add/subtract quantities). Restricted to Admin and Seller roles.
     */
    @PostMapping("/variants/{variantId}/inventory")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER')")
    public ResponseEntity<ApiResponse<Void>> adjustInventory(
            @PathVariable UUID variantId,
            @Valid @RequestBody InventoryAdjustRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        
        User user = userDetails.getUser();
        productService.adjustInventory(variantId, request.getQuantityDelta(), user);
        return ResponseEntity.ok(ApiResponse.success("Inventory adjusted successfully"));
    }

    /**
     * Delete a product. Restricted to Admin and Seller roles.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SELLER')")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable UUID id) {
        productService.deleteProduct(id);
        return ResponseEntity.ok(ApiResponse.success("Product deleted successfully"));
    }
}
