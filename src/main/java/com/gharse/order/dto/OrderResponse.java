package com.gharse.order.dto;

import com.gharse.order.FulfilmentMode;
import com.gharse.order.Order;
import com.gharse.order.OrderItem;
import com.gharse.order.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;

public record OrderResponse(Long orderId, Long paymentId, Long buyerId,
        Long sellerProfileId, OrderStatus status, FulfilmentMode fulfilmentModeSelected,
        String deliveryAddressSnapshot, LocalDateTime createdAt, LocalDateTime updatedAt,
        List<OrderItemResponse> items) {
    public static OrderResponse from(Order order, List<OrderItem> items) {
        return new OrderResponse(order.getId(), order.getPaymentId(), order.getBuyerId(),
            order.getSellerProfileId(), order.getStatus(), order.getFulfilmentModeSelected(),
            order.getDeliveryAddressSnapshot(), order.getCreatedAt(), order.getUpdatedAt(),
            items.stream().map(OrderItemResponse::from).toList());
    }
}
