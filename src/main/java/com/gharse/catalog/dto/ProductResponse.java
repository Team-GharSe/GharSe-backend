package com.gharse.catalog.dto;

import com.gharse.catalog.FulfilmentType;

import java.math.BigDecimal;
import java.util.List;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Long categoryId,
        String categoryName,
        Long sellerId,
        String sellerName,
        FulfilmentType fulfilmentType,
        Integer inventoryQty,
        Integer productionTimeMinutesPerUnit,
        boolean supportsSelfDelivery,
        boolean supportsPickup,
        boolean supportsPostal,
        BigDecimal deliveryRadiusKm,
        boolean verified,
        BigDecimal averageRating,
        int reviewCount,
        boolean active,
        boolean inStock,
        String imageUrl,
        List<Photo> images
) {

    /** One photo. The id is what the seller sends to remove it: DELETE /v1/products/{id}/photos/{imageId}. */
    public record Photo(Long id, String imageUrl, int displayOrder) {
    }
}