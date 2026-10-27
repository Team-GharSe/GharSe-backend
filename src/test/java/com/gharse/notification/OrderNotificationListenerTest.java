package com.gharse.notification;

import com.gharse.order.FulfilmentMode;
import com.gharse.order.OrderStatus;
import com.gharse.order.OrderStatusChangedEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderNotificationListenerTest {

    private final NotificationService service = mock(NotificationService.class);
    private final OrderNotificationListener listener = new OrderNotificationListener(service);
    private final OrderStatusChangedEvent event =
            new OrderStatusChangedEvent(12L, 1L, 3L, FulfilmentMode.PICKUP, null, OrderStatus.CONFIRMED);

    @Test
    void passesTheEventToTheService() {
        listener.onOrderStatusChanged(event);
        verify(service).notifyBuyer(event);
    }

    @Test
    void swallowsAnyFailureSoThePublisherIsNeverAffected() {
        doThrow(new IllegalStateException("db down")).when(service).notifyBuyer(event);
        assertDoesNotThrow(() -> listener.onOrderStatusChanged(event));
    }
}