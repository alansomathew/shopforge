package com.codewithalanso.shopforge.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductDetailResponse {
    private UUID id;
    private String slug;
    private String name;
    private String description;
    private String shortDescription;
    private String brand;
    private String category;
    private String categorySlug;
    private String primaryImage;
    private List<String> images;
    private BigDecimal basePrice;
    private BigDecimal salePrice;
    private BigDecimal avgRating;
    private int reviewCount;
    private boolean isNew;
    private String badge; // 'SALE' | 'NEW' | 'HOT'
    private boolean inStock;
    private List<ProductVariantDto> variants;
}
