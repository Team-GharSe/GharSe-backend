package com.gharse.cart;

import java.math.BigDecimal;
import java.util.List;

public record CartView(
        Long sellerProfileId,
        String sellerName,
        List<Line> items,
        BigDecimal subtotal) {

    public record Line(
            Long productId,
            String name,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal lineTotal,
            boolean available) {
    }

    public static CartView empty() {
        return new CartView(null, null, List.of(), BigDecimal.ZERO.setScale(2));
    }
}