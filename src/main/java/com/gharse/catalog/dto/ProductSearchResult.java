package com.gharse.catalog.dto;

import java.math.BigDecimal;

public record ProductSearchResult(
        Long id,
        String name,
        BigDecimal price,
        Long categoryId,
        String categoryName,
        Long sellerProfileId,
        String sellerName,
        String fulfilmentType,
        boolean inStock,
        String imageUrl,
        boolean verified,
        BigDecimal averageRating,
        int reviewCount,
        Double distanceKm
) {
}