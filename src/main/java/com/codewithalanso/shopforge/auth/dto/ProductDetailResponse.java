package com.codewithalanso.shopforge.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    // See CategoryResponse.isActive for the full explanation: without @JsonProperty, Jackson
    // serializes Lombok's isNew() getter as JSON key "new", not "isNew", silently breaking the
    // frontend's Product.isNew field (the "NEW" badge logic) -- discovered by actually running
    // the app and inspecting a real response, not visible from the Java code in isolation.
    @JsonProperty("isNew")
    private boolean isNew;
    private String badge; // 'SALE' | 'NEW' | 'HOT'
    private boolean inStock;
    private List<ProductVariantDto> variants;
}
