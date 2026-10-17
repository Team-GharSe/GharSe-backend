package com.gharse.order.dto;

import com.gharse.order.OrderItem;
import java.math.BigDecimal;

public record OrderItemResponse(Long orderItemId, Long productId,
        String productNameSnapshot, BigDecimal priceSnapshot, Integer quantity) {
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(item.getId(), item.getProductId(),
            item.getProductNameSnapshot(), item.getPriceSnapshot(), item.getQuantity());
    }
}
