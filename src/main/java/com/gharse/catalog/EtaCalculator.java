package com.gharse.catalog;

import com.gharse.order.OrderItemRepository;
import com.gharse.order.OrderStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
public class EtaCalculator {

    private static final List<OrderStatus> BACKLOG_STATUSES =
            List.of(OrderStatus.CONFIRMED, OrderStatus.IN_PRODUCTION);
    private static final int MINUTES_PER_DAY = 1440;

    private final OrderItemRepository orderItems;

    public EtaCalculator(OrderItemRepository orderItems) {
        this.orderItems = orderItems;
    }

    /**
     * Estimated ready date for a new quantity of a made-to-order product. The seller works one queue,
     * so the order waits behind the seller's whole backlog (every made-to-order listing, in minutes),
     * then takes quantity x this product's minutes per unit. Computed live; nothing stored.
     */
    public LocalDate estimatedReadyDate(Product product, int quantity) {
        if (quantity < 1) {
            throw new IllegalArgumentException("quantity must be at least 1");
        }
        long minutesPerUnit = product.getProductionTimeMinutesPerUnit() == null
                ? 0 : product.getProductionTimeMinutesPerUnit();
        return dateAfter(backlogMinutes(product.getSellerProfileId()) + quantity * minutesPerUnit);
    }

    /** The date the seller's current queue clears, before any new order is added. */
    public LocalDate queueClearDate(Long sellerProfileId) {
        return dateAfter(backlogMinutes(sellerProfileId));
    }

    private long backlogMinutes(Long sellerProfileId) {
        return orderItems.sumBacklogMinutes(sellerProfileId, BACKLOG_STATUSES);
    }

    /** Whole days from today, rounded up. Production is counted as 24 hours a day. */
    private static LocalDate dateAfter(long minutes) {
        return LocalDate.now().plusDays((minutes + MINUTES_PER_DAY - 1) / MINUTES_PER_DAY);
    }
}