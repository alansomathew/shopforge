package com.codewithalanso.shopforge.dto.cart;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * What we actually send back to the frontend for one cart line -- deliberately flatter than the
 * CartItem/ProductVariant/Product entity graph it's built from. The frontend cart page just
 * wants "name, image, price, quantity" to render a row; it shouldn't need to know that a
 * ProductVariant belongs to a Product which has a list of ProductImages, or care about JPA at
 * all. Building this DTO in the service layer (see CartService.mapItemToResponse) is exactly
 * where that flattening/translation belongs.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CartItemResponse {
    private UUID id;
    private UUID variantId;
    private UUID productId;
    private String productSlug;
    private String productName;
    private String variantName;
    private String sku;
    private String image;
    private BigDecimal unitPrice;
    private int quantity;
    private BigDecimal lineTotal;

    /** Current sellable stock for this variant, so the UI can warn "only 2 left". */
    private int availableStock;
}
