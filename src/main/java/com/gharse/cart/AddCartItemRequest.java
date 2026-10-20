package com.gharse.cart;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AddCartItemRequest(
        @NotNull Long productId,
        @NotNull @Min(1) @Max(CartService.MAX_QUANTITY_PER_ITEM) Integer quantity) {
}