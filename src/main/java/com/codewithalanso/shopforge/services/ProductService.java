package com.codewithalanso.shopforge.services;

import com.codewithalanso.shopforge.auth.dto.ProductDetailResponse;
import com.codewithalanso.shopforge.auth.dto.ProductListResponseDto;
import com.codewithalanso.shopforge.auth.dto.ProductVariantDto;
import com.codewithalanso.shopforge.auth.dto.ProductSaveRequest;
import com.codewithalanso.shopforge.common.exception.AppException;
import com.codewithalanso.shopforge.entities.*;
import com.codewithalanso.shopforge.repositories.*;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final CategoryService categoryService;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final InventoryTransactionRepository inventoryTransactionRepository;

    /**
     * Search and filter products using dynamic criteria.
     *
     * @Transactional(readOnly = true) matters here for the same reason as in CartService:
     * application.properties sets spring.jpa.open-in-view=false, so the Hibernate session
     * normally closes the instant a repository call (productRepository.findAll(spec, pageable))
     * returns -- it does NOT stay open for the rest of this method. mapToDetailResponse() below
     * walks product -> variants / images, both lazy @OneToMany collections, and without this
     * annotation keeping the session open for the whole method, that access throws
     * LazyInitializationException ("no session") the moment it's touched. This was a real,
     * pre-existing bug: this method had no @Transactional at all before, so browsing the product
     * catalog failed on literally any request that reached this Specification-based path
     * (i.e. anything except a plain search-only query).
     */
    @Transactional(readOnly = true)
    public ProductListResponseDto getProducts(
            String search,
            String categorySlug,
            String brandSlug,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer minRating,
            Boolean inStock,
            Boolean featured,
            String sortBy,
            int page,
            int size) {

        // 1. Handle sorting
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt"); // default
        if (sortBy != null) {
            switch (sortBy.toLowerCase()) {
                case "price-asc":
                    sort = Sort.by(Sort.Direction.ASC, "basePrice");
                    break;
                case "price-desc":
                    sort = Sort.by(Sort.Direction.DESC, "basePrice");
                    break;
                case "rating":
                    sort = Sort.by(Sort.Direction.DESC, "avgRating");
                    break;
                case "newest":
                    sort = Sort.by(Sort.Direction.DESC, "createdAt");
                    break;
                case "popular":
                    sort = Sort.by(Sort.Direction.DESC, "viewCount");
                    break;
            }
        }

        Pageable pageable = PageRequest.of(page, size, sort);

        // 2. If it's a full-text search with query and no other strict filters, use PG Full-Text Search
        if (search != null && !search.trim().isEmpty() && categorySlug == null && brandSlug == null && minPrice == null && maxPrice == null && minRating == null) {
            Page<Product> searchResult = productRepository.searchProducts(search.trim(), pageable);
            return mapToPageResponse(searchResult);
        }

        // 3. Dynamic Filtering using JPA Specification
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Only show active products. There's no separate "not deleted" predicate needed here:
            // Product carries @SQLRestriction("deleted_at IS NULL") at the class level (see the
            // entity), which Hibernate silently appends to every query against this entity on
            // its own. A previous version of this line tried to do that filtering manually via
            // root.get("deleted_at") -- but JPA Criteria's root.get(...) takes the ENTITY's Java
            // property name (deletedAt), not the raw database column name, so it threw
            // "Could not resolve attribute 'deleted_at'" on every request that reached this
            // Specification (i.e. any product listing without a plain search term).
            predicates.add(cb.equal(root.get("status"), ProductStatus.ACTIVE));

            // Featured filter
            if (featured != null) {
                predicates.add(cb.equal(root.get("isFeatured"), featured));
            }

            // Search query filter (fallback to simple like if CTE not used alone)
            if (search != null && !search.trim().isEmpty()) {
                String searchPattern = "%" + search.trim().toLowerCase() + "%";
                Predicate nameLike = cb.like(cb.lower(root.get("name")), searchPattern);
                Predicate descLike = cb.like(cb.lower(root.get("description")), searchPattern);
                predicates.add(cb.or(nameLike, descLike));
            }

            // Category filter (includes subcategories)
            if (categorySlug != null) {
                Optional<Category> categoryOpt = categoryService.getCategoryBySlug(categorySlug);
                if (categoryOpt.isPresent()) {
                    Set<UUID> categoryIds = categoryService.getDescendantCategoryIds(categoryOpt.get().getId());
                    predicates.add(root.get("category").get("id").in(categoryIds));
                } else {
                    // Category not found, return empty results
                    predicates.add(cb.disjunction());
                }
            }

            // Brand filter
            if (brandSlug != null) {
                predicates.add(cb.equal(root.get("brand").get("slug"), brandSlug));
            }

            // Price range filter
            if (minPrice != null) {
                Expression<BigDecimal> effectivePrice = cb.coalesce(root.get("salePrice"), root.get("basePrice"));
                predicates.add(cb.greaterThanOrEqualTo(effectivePrice, minPrice));
            }
            if (maxPrice != null) {
                Expression<BigDecimal> effectivePrice = cb.coalesce(root.get("salePrice"), root.get("basePrice"));
                predicates.add(cb.lessThanOrEqualTo(effectivePrice, maxPrice));
            }

            // Rating filter
            if (minRating != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("avgRating"), BigDecimal.valueOf(minRating)));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Product> productPage = productRepository.findAll(spec, pageable);
        
        // Post-filtering for inStock if required (standard pagination limit applies)
        if (inStock != null && inStock) {
            // Filter products in memory if their variants have positive stock
            List<ProductDetailResponse> mappedList = productPage.getContent().stream()
                    .map(this::mapToDetailResponse)
                    .filter(ProductDetailResponse::isInStock)
                    .collect(Collectors.toList());
            
            return ProductListResponseDto.builder()
                    .content(mappedList)
                    .pageNumber(productPage.getNumber())
                    .pageSize(productPage.getSize())
                    .totalElements(mappedList.size()) // approximated for memory-filtered sublist
                    .totalPages(productPage.getTotalPages())
                    .last(productPage.isLast())
                    .build();
        }

        return mapToPageResponse(productPage);
    }

    /**
     * Get detailed product response by slug. Not readOnly like getProducts() above -- this
     * method also increments the view count (an actual write) in the same transaction, and a
     * read-only transaction can have writes rejected at the JDBC driver/database level.
     */
    @Transactional
    public Optional<ProductDetailResponse> getProductBySlug(String slug) {
        return productRepository.findBySlugAndStatus(slug, ProductStatus.ACTIVE)
                .map(product -> {
                    // Increment view count asynchronously/simply
                    product.setViewCount(product.getViewCount() + 1);
                    productRepository.save(product);
                    return mapToDetailResponse(product);
                });
    }

    private ProductListResponseDto mapToPageResponse(Page<Product> page) {
        List<ProductDetailResponse> dtoList = page.getContent().stream()
                .map(this::mapToDetailResponse)
                .collect(Collectors.toList());

        return ProductListResponseDto.builder()
                .content(dtoList)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    private ProductDetailResponse mapToDetailResponse(Product product) {
        List<ProductVariantDto> variants = product.getVariants().stream()
                .map(variant -> {
                    int availableStock = inventoryRepository.findAvailableQuantityByVariantId(variant.getId())
                            .orElse(0);
                    
                    return ProductVariantDto.builder()
                            .id(variant.getId())
                            .sku(variant.getSku())
                            .name(variant.getName())
                            .price(variant.getPrice())
                            .salePrice(variant.getSalePrice())
                            .attributes(variant.getAttributes())
                            .stock(availableStock)
                            .build();
                })
                .collect(Collectors.toList());

        boolean inStock = variants.stream().anyMatch(v -> v.getStock() > 0);
        
        // Find primary image url
        String primaryImage = product.getImages().stream()
                .filter(ProductImage::isPrimary)
                .map(ProductImage::getUrl)
                .findFirst()
                .orElse(product.getImages().isEmpty() ? "" : product.getImages().get(0).getUrl());

        List<String> images = product.getImages().stream()
                .map(ProductImage::getUrl)
                .collect(Collectors.toList());

        // Determine if it is new (e.g. added in the last 7 days)
        boolean isNew = product.getCreatedAt().isAfter(java.time.Instant.now().minusSeconds(7 * 24 * 60 * 60));

        // Determine badge
        String badge = null;
        if (product.getSalePrice() != null) {
            badge = "SALE";
        } else if (isNew) {
            badge = "NEW";
        } else if (product.isFeatured()) {
            badge = "HOT";
        }

        return ProductDetailResponse.builder()
                .id(product.getId())
                .slug(product.getSlug())
                .name(product.getName())
                .description(product.getDescription())
                .shortDescription(product.getShortDescription())
                .brand(product.getBrand() != null ? product.getBrand().getName() : "")
                .category(product.getCategory().getName())
                .categorySlug(product.getCategory().getSlug())
                .primaryImage(primaryImage)
                .images(images)
                .basePrice(product.getBasePrice())
                .salePrice(product.getSalePrice())
                .avgRating(product.getAvgRating() != null ? product.getAvgRating() : BigDecimal.ZERO)
                .reviewCount(product.getReviewCount())
                .isNew(isNew)
                .badge(badge)
                .inStock(inStock)
                .variants(variants)
                .build();
    }

    /**
     * Create a new product with variants and initial stock.
     */
    @Transactional
    public ProductDetailResponse saveProduct(ProductSaveRequest request, User seller) {
        Category category = categoryRepository.findBySlug(request.getCategorySlug())
                .orElseThrow(() -> new AppException("Category not found", HttpStatus.NOT_FOUND));

        Brand brand = null;
        if (request.getBrandSlug() != null && !request.getBrandSlug().trim().isEmpty()) {
            brand = brandRepository.findBySlug(request.getBrandSlug())
                    .orElseThrow(() -> new AppException("Brand not found", HttpStatus.NOT_FOUND));
        }

        // Generate clean URL slug
        String slug = request.getName().toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
        
        // Ensure slug uniqueness
        int count = 1;
        String originalSlug = slug;
        while (productRepository.findBySlugAndStatus(slug, ProductStatus.ACTIVE).isPresent() ||
               productRepository.findBySlugAndStatus(slug, ProductStatus.DRAFT).isPresent()) {
            slug = originalSlug + "-" + (count++);
        }

        Product product = Product.builder()
                .seller(seller)
                .category(category)
                .brand(brand)
                .name(request.getName())
                .slug(slug)
                .description(request.getDescription())
                .shortDescription(request.getShortDescription())
                .status(request.getStatus())
                .isFeatured(request.isFeatured())
                .hasVariants(request.isHasVariants())
                .basePrice(request.getBasePrice())
                .salePrice(request.getSalePrice())
                .images(new ArrayList<>())
                .variants(new ArrayList<>())
                .build();

        Product savedProduct = productRepository.save(product);

        // Save images
        if (request.getImageUrls() != null) {
            int sortOrder = 0;
            for (String url : request.getImageUrls()) {
                ProductImage img = ProductImage.builder()
                        .product(savedProduct)
                        .url(url)
                        .isPrimary(sortOrder == 0)
                        .sortOrder(sortOrder++)
                        .build();
                savedProduct.getImages().add(productImageRepository.save(img));
            }
        }

        // Save variants
        if (request.getVariants() != null && !request.getVariants().isEmpty()) {
            int sortOrder = 0;
            for (ProductSaveRequest.VariantSaveRequest vReq : request.getVariants()) {
                ProductVariant variant = ProductVariant.builder()
                        .product(savedProduct)
                        .sku(vReq.getSku())
                        .name(vReq.getName())
                        .price(vReq.getPrice())
                        .salePrice(vReq.getSalePrice())
                        .attributes(vReq.getAttributes() != null ? vReq.getAttributes() : Map.of())
                        .isActive(true)
                        .sortOrder(sortOrder++)
                        .build();
                ProductVariant savedVariant = productVariantRepository.save(variant);
                savedProduct.getVariants().add(savedVariant);

                // Initialize inventory record
                Inventory inventory = Inventory.builder()
                        .variant(savedVariant)
                        .quantityOnHand(0)
                        .quantityReserved(0)
                        .reorderThreshold(10)
                        .build();
                inventoryRepository.save(inventory);

                // If stock is specified, adjust inventory
                if (vReq.getStock() > 0) {
                    InventoryTransaction transaction = InventoryTransaction.builder()
                            .variant(savedVariant)
                            .type(InventoryTransactionType.ADJUSTMENT)
                            .quantityDelta(vReq.getStock())
                            .note("Initial stock seeding")
                            .createdBy(seller)
                            .build();
                    inventoryTransactionRepository.save(transaction);
                }
            }
        }

        return mapToDetailResponse(savedProduct);
    }

    /**
     * Update an existing product.
     */
    @Transactional
    public ProductDetailResponse updateProduct(UUID id, ProductSaveRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new AppException("Product not found", HttpStatus.NOT_FOUND));

        Category category = categoryRepository.findBySlug(request.getCategorySlug())
                .orElseThrow(() -> new AppException("Category not found", HttpStatus.NOT_FOUND));

        Brand brand = null;
        if (request.getBrandSlug() != null && !request.getBrandSlug().trim().isEmpty()) {
            brand = brandRepository.findBySlug(request.getBrandSlug())
                    .orElseThrow(() -> new AppException("Brand not found", HttpStatus.NOT_FOUND));
        }

        product.setCategory(category);
        product.setBrand(brand);
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setShortDescription(request.getShortDescription());
        product.setStatus(request.getStatus());
        product.setFeatured(request.isFeatured());
        product.setHasVariants(request.isHasVariants());
        product.setBasePrice(request.getBasePrice());
        product.setSalePrice(request.getSalePrice());

        // Update images (clear old ones, add new ones)
        if (product.getImages() != null && !product.getImages().isEmpty()) {
            productImageRepository.deleteAll(product.getImages());
            product.getImages().clear();
        }

        if (request.getImageUrls() != null) {
            int sortOrder = 0;
            for (String url : request.getImageUrls()) {
                ProductImage img = ProductImage.builder()
                        .product(product)
                        .url(url)
                        .isPrimary(sortOrder == 0)
                        .sortOrder(sortOrder++)
                        .build();
                product.getImages().add(productImageRepository.save(img));
            }
        }

        // Sync/update variants
        if (request.getVariants() != null) {
            for (ProductSaveRequest.VariantSaveRequest vReq : request.getVariants()) {
                ProductVariant variant = productVariantRepository.findBySku(vReq.getSku()).orElse(null);
                if (variant == null) {
                    variant = ProductVariant.builder()
                            .product(product)
                            .sku(vReq.getSku())
                            .name(vReq.getName())
                            .price(vReq.getPrice())
                            .salePrice(vReq.getSalePrice())
                            .attributes(vReq.getAttributes() != null ? vReq.getAttributes() : Map.of())
                            .isActive(true)
                            .build();
                    ProductVariant savedVariant = productVariantRepository.save(variant);
                    product.getVariants().add(savedVariant);

                    Inventory inventory = Inventory.builder()
                            .variant(savedVariant)
                            .quantityOnHand(0)
                            .quantityReserved(0)
                            .reorderThreshold(10)
                            .build();
                    inventoryRepository.save(inventory);

                    if (vReq.getStock() > 0) {
                        InventoryTransaction transaction = InventoryTransaction.builder()
                                .variant(savedVariant)
                                .type(InventoryTransactionType.ADJUSTMENT)
                                .quantityDelta(vReq.getStock())
                                .note("Variant initial stock")
                                .createdBy(product.getSeller())
                                .build();
                        inventoryTransactionRepository.save(transaction);
                    }
                } else {
                    variant.setName(vReq.getName());
                    variant.setPrice(vReq.getPrice());
                    variant.setSalePrice(vReq.getSalePrice());
                    variant.setAttributes(vReq.getAttributes() != null ? vReq.getAttributes() : Map.of());
                    productVariantRepository.save(variant);
                }
            }
        }

        Product savedProduct = productRepository.save(product);
        return mapToDetailResponse(savedProduct);
    }

    /**
     * Adjust inventory stock by adding or subtracting quantities.
     */
    @Transactional
    public void adjustInventory(UUID variantId, int quantityDelta, User user) {
        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new AppException("Product variant not found", HttpStatus.NOT_FOUND));

        InventoryTransaction transaction = InventoryTransaction.builder()
                .variant(variant)
                .type(InventoryTransactionType.ADJUSTMENT)
                .quantityDelta(quantityDelta)
                .note("Manual inventory adjustment")
                .createdBy(user)
                .build();

        inventoryTransactionRepository.save(transaction);
    }

    /**
     * Soft delete a product by setting deleted_at to current timestamp.
     */
    @Transactional
    public void deleteProduct(UUID id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new AppException("Product not found", HttpStatus.NOT_FOUND));
        product.setDeletedAt(java.time.Instant.now());
        productRepository.save(product);
    }
}
