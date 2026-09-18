package com.codewithalanso.shopforge.dto.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Same flattened-page shape as ProductListResponseDto -- kept consistent across every list endpoint. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderListResponseDto {
    private List<OrderResponse> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
    private boolean last;
}
