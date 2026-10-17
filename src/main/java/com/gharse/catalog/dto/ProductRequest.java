package com.gharse.catalog.dto;

import com.gharse.catalog.FulfilmentType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank @Size(max = 150) String name,
        @Size(max = 5000) String description,
        @NotNull @DecimalMin("0.01") BigDecimal price,
        @NotNull Long categoryId,
        @NotNull FulfilmentType fulfilmentType,
        @Min(0) Integer inventoryQty,
        @Min(1) Integer productionTimeMinutesPerUnit,
        Boolean supportsSelfDelivery,
        Boolean supportsPickup,
        Boolean supportsPostal,
        @DecimalMin("0.1") BigDecimal deliveryRadiusKm
) {
}