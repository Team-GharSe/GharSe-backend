package com.gharse.order;

public record OrderStatusChangedEvent(
        Long orderId,
        Long buyerId,
        Long sellerProfileId,
        FulfilmentMode fulfilmentMode,
        OrderStatus previousStatus,
        OrderStatus newStatus) {

    public static OrderStatusChangedEvent of(Order order, OrderStatus previous) {
        return new OrderStatusChangedEvent(order.getId(), order.getBuyerId(), order.getSellerProfileId(),
                order.getFulfilmentModeSelected(), previous, order.getStatus());
    }
}