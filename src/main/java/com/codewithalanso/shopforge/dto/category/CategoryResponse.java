package com.codewithalanso.shopforge.dto.category;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * A flat (non-tree) category row for the admin management screen -- unlike CategoryNodeDto
 * (auth/dto/CategoryNodeDto.java), which nests children for the public storefront's category
 * tree and only ever includes active categories. Admins need to see and re-activate inactive
 * categories too, which a tree built only from active rows could never represent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {
    private UUID id;
    private UUID parentId;
    private String name;
    private String slug;
    private String description;
    private String imageUrl;
    private int sortOrder;

    // Without @JsonProperty, Jackson derives the JSON key from the Lombok-generated getter name
    // (isActive()) by stripping the "is" prefix for boolean getters -- so this would otherwise
    // serialize as "active", not "isActive", silently breaking the frontend's Category.isActive
    // field (it would just always read as undefined). Confirmed by actually hitting the running
    // endpoint and inspecting the raw JSON, not something visible from the Java code alone.
    @JsonProperty("isActive")
    private boolean isActive;
}
