package com.gharse.order;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class OrderStatusChangedEventTest {

    private Order order() {
        Order o = new Order(77L, 1L, 3L, FulfilmentMode.POSTAL, "12 Main St");
        ReflectionTestUtils.setField(o, "id", 12L);
        return o;
    }

    @Test
    void bookingEventHasNoPreviousStatus() {
        OrderStatusChangedEvent e = OrderStatusChangedEvent.of(order(), null);

        assertEquals(12L, e.orderId());
        assertEquals(1L, e.buyerId());
        assertEquals(3L, e.sellerProfileId());
        assertEquals(FulfilmentMode.POSTAL, e.fulfilmentMode());
        assertNull(e.previousStatus());
        assertEquals(OrderStatus.CONFIRMED, e.newStatus());
    }

    @Test
    void transitionEventCarriesBothStatuses() {
        Order o = order();
        o.transitionTo(OrderStatus.IN_PRODUCTION);

        OrderStatusChangedEvent e = OrderStatusChangedEvent.of(o, OrderStatus.CONFIRMED);

        assertEquals(OrderStatus.CONFIRMED, e.previousStatus());
        assertEquals(OrderStatus.IN_PRODUCTION, e.newStatus());
    }
}