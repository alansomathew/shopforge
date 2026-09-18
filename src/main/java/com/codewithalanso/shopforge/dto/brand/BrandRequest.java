package com.codewithalanso.shopforge.dto.brand;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BrandRequest {

    @NotBlank(message = "name is required")
    private String name;

    private String logoUrl;
    private String description;
}
