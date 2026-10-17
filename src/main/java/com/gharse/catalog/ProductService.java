package com.gharse.catalog;

import com.gharse.auth.AuthenticatedUser;
import com.gharse.catalog.dto.ProductRequest;
import com.gharse.catalog.dto.ProductResponse;
import com.gharse.common.exception.BadRequestException;
import com.gharse.common.exception.ResourceNotFoundException;
import com.gharse.seller.SellerProfile;
import com.gharse.seller.SellerProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductService {

    private final ProductRepository products;
    private final ProductImageRepository images;
    private final CategoryRepository categories;
    private final SellerProfileRepository sellers;

    public ProductService(ProductRepository products,
                          ProductImageRepository images,
                          CategoryRepository categories,
                          SellerProfileRepository sellers) {
        this.products = products;
        this.images = images;
        this.categories = categories;
        this.sellers = sellers;
    }

    @Transactional
    public ProductResponse create(AuthenticatedUser user, ProductRequest req) {
        SellerProfile seller = currentSeller(user);
        requireCategory(req.categoryId());

        Product product = new Product();
        product.setSellerProfileId(seller.getId());
        apply(product, req);
        return toResponse(products.save(product));
    }

    @Transactional
    public ProductResponse update(AuthenticatedUser user, Long productId, ProductRequest req) {
        SellerProfile seller = currentSeller(user);
        Product product = ownedProduct(productId, seller);
        requireCategory(req.categoryId());

        apply(product, req);
        return toResponse(products.save(product));
    }

    /** Soft delete (FR20). Calling it twice is harmless. */
    @Transactional
    public void deactivate(AuthenticatedUser user, Long productId) {
        SellerProfile seller = currentSeller(user);
        Product product = ownedProduct(productId, seller);
        product.setActive(false);
        products.save(product);
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> listMine(AuthenticatedUser user) {
        SellerProfile seller = currentSeller(user);
        return products.findBySellerProfileIdOrderByCreatedAtDesc(seller.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private SellerProfile currentSeller(AuthenticatedUser user) {
        return sellers.findByUserId(user.id())
                .orElseThrow(() -> new ResourceNotFoundException("Create a seller profile before adding products."));
    }

    /**
     * Returns the product only if this seller owns it. Anyone else gets the same
     * 404 as a missing product, so ids can't be probed.
     */
    private Product ownedProduct(Long productId, SellerProfile seller) {
        return products.findByIdAndSellerProfileId(productId, seller.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
    }

    private void requireCategory(Long categoryId) {
        if (!categories.existsById(categoryId)) {
            throw new BadRequestException("That category doesn't exist.");
        }
    }

    /** Copies request fields onto the entity and enforces the fulfilment rules. */
    private void apply(Product p, ProductRequest r) {
        if (!r.supportsSelfDelivery() && !r.supportsPickup() && !r.supportsPostal()) {
            throw new BadRequestException("Choose at least one way to fulfil this product: self delivery, pickup or postal.");
        }
        if (r.supportsSelfDelivery() && r.deliveryRadiusKm() == null) {
            throw new BadRequestException("A delivery radius is needed for self delivery.");
        }

        if (r.fulfilmentType() == FulfilmentType.READY_STOCK) {
            if (r.inventoryQty() == null) {
                throw new BadRequestException("Ready-stock products need a quantity.");
            }
            p.setInventoryQty(r.inventoryQty());
            p.setProductionTimeMinutesPerUnit(null);
        } else {
            if (r.productionTimeMinutesPerUnit() == null) {
                throw new BadRequestException("Made-to-order products need a production time per unit.");
            }
            p.setProductionTimeMinutesPerUnit(r.productionTimeMinutesPerUnit());
            p.setInventoryQty(null);
        }

        p.setName(r.name().trim());
        p.setDescription(r.description());
        p.setPrice(r.price());
        p.setCategoryId(r.categoryId());
        p.setFulfilmentType(r.fulfilmentType());
        p.setSupportsSelfDelivery(r.supportsSelfDelivery());
        p.setSupportsPickup(r.supportsPickup());
        p.setSupportsPostal(r.supportsPostal());
        p.setDeliveryRadiusKm(r.supportsSelfDelivery() ? r.deliveryRadiusKm() : null);
    }

    private ProductResponse toResponse(Product p) {
        String categoryName = categories.findById(p.getCategoryId())
                .map(Category::getName)
                .orElse("");
        String sellerName = sellers.findById(p.getSellerProfileId())
                .map(SellerProfile::getBusinessName)
                .orElse("");
        List<String> imageUrls = images.findByProductIdOrderByDisplayOrderAsc(p.getId())
                .stream()
                .map(ProductImage::getImageUrl)
                .toList();

        boolean inStock = p.getFulfilmentType() == FulfilmentType.MADE_TO_ORDER
                || (p.getInventoryQty() != null && p.getInventoryQty() > 0);

        return new ProductResponse(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getPrice(),
                p.getCategoryId(),
                categoryName,
                p.getSellerProfileId(),
                sellerName,
                p.getFulfilmentType(),
                p.getInventoryQty(),
                p.getProductionTimeMinutesPerUnit(),
                p.isSupportsSelfDelivery(),
                p.isSupportsPickup(),
                p.isSupportsPostal(),
                p.getDeliveryRadiusKm(),
                p.isVerified(),
                p.getAverageRating(),
                p.getReviewCount(),
                p.isActive(),
                inStock,
                imageUrls.isEmpty() ? null : imageUrls.get(0),
                imageUrls
        );
    }
}
