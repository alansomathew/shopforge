package com.codewithalanso.shopforge.dto.brand;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandResponse {
    private UUID id;
    private String name;
    private String slug;
    private String logoUrl;
    private String description;

    // See CategoryResponse.isActive for why this annotation is required: without it, Jackson
    // serializes Lombok's isActive() getter as JSON key "active", not "isActive".
    @JsonProperty("isActive")
    private boolean isActive;
}
