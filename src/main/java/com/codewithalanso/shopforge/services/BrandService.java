package com.codewithalanso.shopforge.services;

import com.codewithalanso.shopforge.common.exception.AppException;
import com.codewithalanso.shopforge.dto.brand.BrandRequest;
import com.codewithalanso.shopforge.dto.brand.BrandResponse;
import com.codewithalanso.shopforge.entities.Brand;
import com.codewithalanso.shopforge.repositories.BrandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BrandService {

    private final BrandRepository brandRepository;

    @Transactional(readOnly = true)
    public List<BrandResponse> getAllBrands() {
        return brandRepository.findAll().stream()
                .sorted(Comparator.comparing(Brand::getName))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public BrandResponse createBrand(BrandRequest request) {
        Brand brand = Brand.builder()
                .name(request.getName())
                .slug(generateUniqueSlug(request.getName()))
                .logoUrl(request.getLogoUrl())
                .description(request.getDescription())
                .build();
        return mapToResponse(brandRepository.save(brand));
    }

    @Transactional
    public BrandResponse updateBrand(UUID id, BrandRequest request) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new AppException("Brand not found", HttpStatus.NOT_FOUND));
        brand.setName(request.getName());
        brand.setLogoUrl(request.getLogoUrl());
        brand.setDescription(request.getDescription());
        return mapToResponse(brandRepository.save(brand));
    }

    /**
     * Deactivate rather than delete. products.brand_id is ON DELETE SET NULL (not RESTRICT like
     * category_id), so a real delete wouldn't fail here -- but it would silently blank out the
     * brand on every product that used it, which is a confusing side effect for an admin who
     * just wanted to hide a brand temporarily. is_active does that safely instead.
     */
    @Transactional
    public void deactivateBrand(UUID id) {
        Brand brand = brandRepository.findById(id)
                .orElseThrow(() -> new AppException("Brand not found", HttpStatus.NOT_FOUND));
        brand.setActive(false);
        brandRepository.save(brand);
    }

    private String generateUniqueSlug(String name) {
        String baseSlug = name.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
        String slug = baseSlug;
        int suffix = 1;
        while (brandRepository.findBySlug(slug).isPresent()) {
            slug = baseSlug + "-" + (suffix++);
        }
        return slug;
    }

    private BrandResponse mapToResponse(Brand brand) {
        return BrandResponse.builder()
                .id(brand.getId())
                .name(brand.getName())
                .slug(brand.getSlug())
                .logoUrl(brand.getLogoUrl())
                .description(brand.getDescription())
                .isActive(brand.isActive())
                .build();
    }
}
