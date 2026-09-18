package com.codewithalanso.shopforge.auth.dto;

import com.codewithalanso.shopforge.entities.ProductStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    // hasVariants below needs no such annotation: Lombok generates isHasVariants() for it (it
    // only keeps a bare "is" getter when the field ALREADY starts with "is", like this one
    // does), and Jackson stripping "is" back off that getter name lands exactly back on
    // "hasVariants" -- the two conventions happen to cancel out. isFeatured has no such luck:
    // see CategoryResponse.isActive for the full explanation of why this one needs it.
    @JsonProperty("isFeatured")
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
