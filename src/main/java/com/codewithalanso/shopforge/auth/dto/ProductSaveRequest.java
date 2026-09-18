package com.codewithalanso.shopforge.auth.dto;

import com.codewithalanso.shopforge.entities.ProductStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class ProductSaveRequest {

    @NotBlank
    private String name;

    private String description;
    private String shortDescription;

    @NotBlank
    private String categorySlug;

    private String brandSlug;

    @NotNull
    private BigDecimal basePrice;

    private BigDecimal salePrice;

    @NotNull
    private ProductStatus status;

    private boolean isFeatured;
    private boolean hasVariants;

    private List<VariantSaveRequest> variants;
    private List<String> imageUrls;

    @Data
    public static class VariantSaveRequest {
        @NotBlank
        private String sku;
        private String name;
        @NotNull
        private BigDecimal price;
        private BigDecimal salePrice;
        private Map<String, Object> attributes;
        private int stock;
    }
}
