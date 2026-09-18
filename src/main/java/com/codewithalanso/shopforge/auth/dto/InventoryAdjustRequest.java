package com.codewithalanso.shopforge.auth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InventoryAdjustRequest {
    @NotNull
    private Integer quantityDelta;
}
