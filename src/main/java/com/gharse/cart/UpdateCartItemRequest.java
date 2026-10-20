package com.gharse.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Use DELETE to remove an item; quantity here must be at least 1. */
public record UpdateCartItemRequest(
        @NotNull @Min(1) @Max(CartService.MAX_QUANTITY_PER_ITEM) Integer quantity) {
}