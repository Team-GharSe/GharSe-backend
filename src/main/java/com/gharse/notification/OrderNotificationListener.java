package com.gharse.notification;

import com.gharse.order.OrderStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationListener.class);

    private final NotificationService notifications;

    public OrderNotificationListener(NotificationService notifications) {
        this.notifications = notifications;
    }

    /** Runs on the notification executor, after the publisher's transaction has committed. */
    @Async(NotificationAsyncConfig.EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        try {
            notifications.notifyBuyer(event);
        } catch (Exception e) {
            log.error("Notification for order {} failed unexpectedly ({})",
                    event.orderId(), e.getClass().getSimpleName());
            log.debug("Unexpected notification failure", e);
        }
    }
}