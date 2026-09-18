package com.codewithalanso.shopforge.dto.category;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

/** Shared by both create (POST) and update (PUT) -- there's no field only one of them needs. */
@Data
public class CategoryRequest {

    @NotBlank(message = "name is required")
    private String name;

    /** Null means "this is a root category" -- same convention as the categories table itself. */
    private UUID parentId;

    private String description;
    private String imageUrl;

    /** Null keeps the current/default sort position instead of forcing it to 0 on every edit. */
    private Integer sortOrder;
}
