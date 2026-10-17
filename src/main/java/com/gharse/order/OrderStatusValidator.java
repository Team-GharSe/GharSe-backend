package com.gharse.order;

import com.gharse.common.exception.ConflictException;

/** FR16/FR17: only the next named state is legal, including for PICKUP orders. */
public final class OrderStatusValidator {
    private OrderStatusValidator() {}

    public static void validate(OrderStatus current, OrderStatus requested) {
        if (current == null || requested == null) {
            throw new ConflictException("Both order statuses must be present.");
        }
        OrderStatus next = switch (current) {
            case CONFIRMED -> OrderStatus.IN_PRODUCTION;
            case IN_PRODUCTION -> OrderStatus.READY;
            case READY -> OrderStatus.OUT_FOR_DELIVERY;
            case OUT_FOR_DELIVERY -> OrderStatus.DELIVERED;
            case DELIVERED -> null;
        };
        if (requested != next) {
            throw new ConflictException("Cannot change order status from " + current + " to " + requested + ".");
        }
    }
}
