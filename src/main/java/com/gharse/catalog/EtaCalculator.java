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

    private final OrderItemRepository orderItems;

    public EtaCalculator(OrderItemRepository orderItems) {
        this.orderItems = orderItems;
    }

    /**
     * Estimated ready date for a new quantity of a made-to-order product,
     * queued behind the seller's current backlog. Computed live; nothing stored.
     */
    public LocalDate estimatedReadyDate(Product product, int quantity) {
        long backlogUnits = orderItems.sumBacklogUnits(product.getId(), BACKLOG_STATUSES);
        long totalUnits = backlogUnits + quantity;
        int minutesPerUnit = product.getProductionTimeMinutesPerUnit() == null
                ? 0 : product.getProductionTimeMinutesPerUnit();
        long totalMinutes = totalUnits * minutesPerUnit;
        return LocalDate.now().plusDays((totalMinutes + 1439) / 1440); // round up to whole days
    }
}