package com.codewithalanso.shopforge.config;

import com.codewithalanso.shopforge.entities.*;
import com.codewithalanso.shopforge.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class SeedDataRunner implements CommandLineRunner {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final InventoryRepository inventoryRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        log.info("Checking database seeding requirements...");

        // 1. Ensure Admin User exists (for seller references)
        User admin = userRepository.findByEmailIgnoreCase("admin@shopforge.com").orElse(null);
        if (admin == null) {
            admin = User.builder()
                    .email("admin@shopforge.com")
                    .emailVerified(true)
                    .firstName("System")
                    .lastName("Admin")
                    .passwordHash(passwordEncoder.encode("Admin@123"))
                    .status(UserStatus.ACTIVE)
                    .failedLoginAttempts((short) 0)
                    .roles(new HashSet<>())
                    .build();
            
            UserRole adminRole = UserRole.builder()
                    .user(admin)
                    .role(Role.ADMIN)
                    .build();
            admin.getRoles().add(adminRole);
            admin = userRepository.save(admin);
            log.info("Admin user seeded successfully.");
        }

        // 1b. Ensure Seller User exists (for testing seller actions)
        User seller = userRepository.findByEmailIgnoreCase("seller@shopforge.com").orElse(null);
        if (seller == null) {
            seller = User.builder()
                    .email("seller@shopforge.com")
                    .emailVerified(true)
                    .firstName("Forge")
                    .lastName("Seller")
                    .passwordHash(passwordEncoder.encode("Seller@123"))
                    .status(UserStatus.ACTIVE)
                    .failedLoginAttempts((short) 0)
                    .roles(new HashSet<>())
                    .build();
            
            UserRole sellerRole = UserRole.builder()
                    .user(seller)
                    .role(Role.SELLER)
                    .build();
            seller.getRoles().add(sellerRole);
            userRepository.save(seller);
            log.info("Seller user seeded successfully.");
        }

        // 2. Seed catalog if empty
        if (categoryRepository.count() == 0) {
            log.info("Seeding catalog categories, brands, products, and inventory stock...");

            // Seeding Categories
            Category electronics = Category.builder()
                    .name("Electronics")
                    .slug("electronics")
                    .description("Gadgets, accessories, smart devices and more.")
                    .imageUrl("https://images.unsplash.com/photo-1505740420928-5e560c06d30e?q=80&w=600&auto=format&fit=crop")
                    .sortOrder(0)
                    .isActive(true)
                    .build();
            
            Category fashion = Category.builder()
                    .name("Fashion")
                    .slug("fashion")
                    .description("Premium clothing, jackets, bags and apparel.")
                    .imageUrl("https://images.unsplash.com/photo-1441986300917-64674bd600d8?q=80&w=600&auto=format&fit=crop")
                    .sortOrder(1)
                    .isActive(true)
                    .build();

            Category homeDecor = Category.builder()
                    .name("Home Decor")
                    .slug("home-decor")
                    .description("Aesthetic furniture, lamps, curtains, and lighting.")
                    .imageUrl("https://images.unsplash.com/photo-1513519245088-0e12902e5a38?q=80&w=600&auto=format&fit=crop")
                    .sortOrder(2)
                    .isActive(true)
                    .build();

            Category kitchenware = Category.builder()
                    .name("Kitchenware")
                    .slug("kitchenware")
                    .description("Chef grade knife sets, pans, and appliances.")
                    .imageUrl("https://images.unsplash.com/photo-1556911220-e15b29be8c8f?q=80&w=600&auto=format&fit=crop")
                    .sortOrder(3)
                    .isActive(true)
                    .build();

            categoryRepository.saveAll(List.of(electronics, fashion, homeDecor, kitchenware));

            // Seeding Brands
            Brand audioForge = Brand.builder().name("AudioForge").slug("audioforge").isActive(true).build();
            Brand urbanThreads = Brand.builder().name("UrbanThreads").slug("urbanthreads").isActive(true).build();
            Brand cozySpace = Brand.builder().name("CozySpace").slug("cozyspace").isActive(true).build();
            Brand chefTech = Brand.builder().name("ChefTech").slug("cheftech").isActive(true).build();
            brandRepository.saveAll(List.of(audioForge, urbanThreads, cozySpace, chefTech));

            // Seeding Products

            // Product 1: ANC Headphones (Electronics)
            Product headphones = Product.builder()
                    .seller(admin)
                    .category(electronics)
                    .brand(audioForge)
                    .name("AudioForge Pro ANC Headphones")
                    .slug("audioforge-pro-headphones")
                    .description("Experience pure sonic isolation with active noise cancelling, custom 40mm drivers, and 45 hours of warm precision acoustics.")
                    .shortDescription("Premium hybrid active noise cancelling headphones.")
                    .status(ProductStatus.ACTIVE)
                    .isFeatured(true)
                    .hasVariants(true)
                    .basePrice(BigDecimal.valueOf(14999))
                    .salePrice(BigDecimal.valueOf(12999))
                    .avgRating(BigDecimal.valueOf(4.8))
                    .reviewCount(120)
                    .build();
            headphones = productRepository.save(headphones);

            ProductImage headphonesImg1 = ProductImage.builder()
                    .product(headphones)
                    .url("https://images.unsplash.com/photo-1505740420928-5e560c06d30e?q=80&w=600&auto=format&fit=crop")
                    .altText("AudioForge Pro Headset")
                    .isPrimary(true)
                    .sortOrder(0)
                    .build();
            productImageRepository.save(headphonesImg1);

            ProductVariant headphonesV1 = ProductVariant.builder()
                    .product(headphones)
                    .sku("AF-PRO-BLK")
                    .name("Matte Black")
                    .price(BigDecimal.valueOf(14999))
                    .salePrice(BigDecimal.valueOf(12999))
                    .attributes(Map.of("Color", "Black"))
                    .isActive(true)
                    .sortOrder(0)
                    .build();
            headphonesV1 = productVariantRepository.save(headphonesV1);

            Inventory headphonesInv1 = Inventory.builder()
                    .variant(headphonesV1)
                    .quantityOnHand(50)
                    .quantityReserved(0)
                    .reorderThreshold(5)
                    .build();
            inventoryRepository.save(headphonesInv1);


            // Product 2: Denim Jacket (Fashion)
            Product jacket = Product.builder()
                    .seller(admin)
                    .category(fashion)
                    .brand(urbanThreads)
                    .name("Urban Classic Denim Jacket")
                    .slug("urban-classic-denim-jacket")
                    .description("Timeless classic fit denim jacket crafted with premium heavy cotton, double stitched chest pockets and clean copper button trims.")
                    .shortDescription("Authentic vintage denim jacket in light wash wash.")
                    .status(ProductStatus.ACTIVE)
                    .isFeatured(true)
                    .hasVariants(true)
                    .basePrice(BigDecimal.valueOf(3999))
                    .salePrice(null)
                    .avgRating(BigDecimal.valueOf(4.5))
                    .reviewCount(45)
                    .build();
            jacket = productRepository.save(jacket);

            ProductImage jacketImg1 = ProductImage.builder()
                    .product(jacket)
                    .url("https://images.unsplash.com/photo-1576995853123-5a10305d93c0?q=80&w=600&auto=format&fit=crop")
                    .altText("Urban Denim Jacket")
                    .isPrimary(true)
                    .sortOrder(0)
                    .build();
            productImageRepository.save(jacketImg1);

            ProductVariant jacketV1 = ProductVariant.builder()
                    .product(jacket)
                    .sku("UT-DJ-MED")
                    .name("Size M")
                    .price(BigDecimal.valueOf(3999))
                    .attributes(Map.of("Size", "M"))
                    .isActive(true)
                    .sortOrder(0)
                    .build();
            jacketV1 = productVariantRepository.save(jacketV1);

            Inventory jacketInv1 = Inventory.builder()
                    .variant(jacketV1)
                    .quantityOnHand(30)
                    .quantityReserved(0)
                    .reorderThreshold(5)
                    .build();
            inventoryRepository.save(jacketInv1);


            // Product 3: Wooden Lamp (Home Decor)
            Product lamp = Product.builder()
                    .seller(admin)
                    .category(homeDecor)
                    .brand(cozySpace)
                    .name("Minimalist Wooden Lamp")
                    .slug("minimalist-wooden-lamp")
                    .description("Warm up your bedside or workspace with our geometric hand-crafted natural oak lamp base paired with a premium textured fabric shade.")
                    .shortDescription("Oak wood base geometric table lamp.")
                    .status(ProductStatus.ACTIVE)
                    .isFeatured(false)
                    .hasVariants(false)
                    .basePrice(BigDecimal.valueOf(2499))
                    .salePrice(BigDecimal.valueOf(1999))
                    .avgRating(BigDecimal.valueOf(4.2))
                    .reviewCount(18)
                    .build();
            lamp = productRepository.save(lamp);

            ProductImage lampImg = ProductImage.builder()
                    .product(lamp)
                    .url("https://images.unsplash.com/photo-1507473885765-e6ed057f782c?q=80&w=600&auto=format&fit=crop")
                    .altText("Wooden Oak Lamp")
                    .isPrimary(true)
                    .sortOrder(0)
                    .build();
            productImageRepository.save(lampImg);

            ProductVariant lampV = ProductVariant.builder()
                    .product(lamp)
                    .sku("CS-LAMP-OAK")
                    .name("Oak Standard")
                    .price(BigDecimal.valueOf(2499))
                    .salePrice(BigDecimal.valueOf(1999))
                    .attributes(Map.of("Wood", "Oak"))
                    .isActive(true)
                    .sortOrder(0)
                    .build();
            lampV = productVariantRepository.save(lampV);

            Inventory lampInv = Inventory.builder()
                    .variant(lampV)
                    .quantityOnHand(15)
                    .quantityReserved(0)
                    .reorderThreshold(2)
                    .build();
            inventoryRepository.save(lampInv);


            // Product 4: Chef Knife Set (Kitchenware) -- the Kitchenware category and ChefTech
            // brand were already seeded above but had no products in them until now.
            Product knifeSet = Product.builder()
                    .seller(admin)
                    .category(kitchenware)
                    .brand(chefTech)
                    .name("ChefTech Professional Knife Set")
                    .slug("cheftech-professional-knife-set")
                    .description("An 8-piece forged high-carbon stainless steel knife set with a walnut block, balanced for all-day prep work in a professional or home kitchen.")
                    .shortDescription("8-piece forged stainless steel chef knife set.")
                    .status(ProductStatus.ACTIVE)
                    .isFeatured(true)
                    .hasVariants(false)
                    .basePrice(BigDecimal.valueOf(5999))
                    .salePrice(BigDecimal.valueOf(4499))
                    .avgRating(BigDecimal.valueOf(4.6))
                    .reviewCount(32)
                    .build();
            knifeSet = productRepository.save(knifeSet);

            ProductImage knifeSetImg = ProductImage.builder()
                    .product(knifeSet)
                    .url("https://images.unsplash.com/photo-1593618998160-e34014e67546?q=80&w=600&auto=format&fit=crop")
                    .altText("ChefTech Knife Set")
                    .isPrimary(true)
                    .sortOrder(0)
                    .build();
            productImageRepository.save(knifeSetImg);

            ProductVariant knifeSetV = ProductVariant.builder()
                    .product(knifeSet)
                    .sku("CT-KNIFE-8PC")
                    .name("8-Piece Set")
                    .price(BigDecimal.valueOf(5999))
                    .salePrice(BigDecimal.valueOf(4499))
                    .attributes(Map.of("Pieces", "8"))
                    .isActive(true)
                    .sortOrder(0)
                    .build();
            knifeSetV = productVariantRepository.save(knifeSetV);

            Inventory knifeSetInv = Inventory.builder()
                    .variant(knifeSetV)
                    .quantityOnHand(25)
                    .quantityReserved(0)
                    .reorderThreshold(5)
                    .build();
            inventoryRepository.save(knifeSetInv);


            // Product 5: Bluetooth Speaker (Electronics)
            Product speaker = Product.builder()
                    .seller(admin)
                    .category(electronics)
                    .brand(audioForge)
                    .name("AudioForge SoundWave Bluetooth Speaker")
                    .slug("audioforge-soundwave-bluetooth-speaker")
                    .description("Portable IPX7 waterproof speaker with 360-degree sound, 24-hour battery life, and deep bass tuning for indoor or outdoor listening.")
                    .shortDescription("Portable waterproof Bluetooth speaker with 24hr battery.")
                    .status(ProductStatus.ACTIVE)
                    .isFeatured(false)
                    .hasVariants(true)
                    .basePrice(BigDecimal.valueOf(4999))
                    .salePrice(BigDecimal.valueOf(3999))
                    .avgRating(BigDecimal.valueOf(4.4))
                    .reviewCount(67)
                    .build();
            speaker = productRepository.save(speaker);

            ProductImage speakerImg = ProductImage.builder()
                    .product(speaker)
                    .url("https://images.unsplash.com/photo-1608043152269-423dbba4e7e1?q=80&w=600&auto=format&fit=crop")
                    .altText("AudioForge SoundWave Speaker")
                    .isPrimary(true)
                    .sortOrder(0)
                    .build();
            productImageRepository.save(speakerImg);

            ProductVariant speakerV = ProductVariant.builder()
                    .product(speaker)
                    .sku("AF-SW-BLK")
                    .name("Midnight Black")
                    .price(BigDecimal.valueOf(4999))
                    .salePrice(BigDecimal.valueOf(3999))
                    .attributes(Map.of("Color", "Black"))
                    .isActive(true)
                    .sortOrder(0)
                    .build();
            speakerV = productVariantRepository.save(speakerV);

            Inventory speakerInv = Inventory.builder()
                    .variant(speakerV)
                    .quantityOnHand(40)
                    .quantityReserved(0)
                    .reorderThreshold(5)
                    .build();
            inventoryRepository.save(speakerInv);

            log.info("Catalog seeding completed successfully.");
        } else {
            log.info("Catalog categories already exist. Skipping seeding.");
        }
    }
}
