package com.codewithalanso.shopforge.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariantDto {
    private UUID id;
    private String sku;
    private String name;
    private BigDecimal price;
    private BigDecimal salePrice;
    private Map<String, Object> attributes;
    private int stock;
}
