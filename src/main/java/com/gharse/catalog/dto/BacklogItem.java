package com.gharse.catalog.dto;

public record BacklogItem(
        Long productId,
        String productName,
        long unitsCommitted,
        int productionTimeMinutesPerUnit,
        String estimatedClearDate) {
}