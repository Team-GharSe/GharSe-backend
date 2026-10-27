package com.gharse.notification;

import com.gharse.order.FulfilmentMode;
import com.gharse.order.OrderStatusChangedEvent;
import com.gharse.seller.SellerProfile;
import com.gharse.seller.SellerProfileRepository;
import com.gharse.user.User;
import com.gharse.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class NotificationService {

    static final String TYPE_ORDER_STATUS_CHANGE = "ORDER_STATUS_CHANGE";

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notifications;
    private final UserRepository users;
    private final SellerProfileRepository sellers;
    private final Map<NotificationChannel, NotificationSender> senders = new EnumMap<>(NotificationChannel.class);

    public NotificationService(NotificationRepository notifications,
                               UserRepository users,
                               SellerProfileRepository sellers,
                               List<NotificationSender> senderList) {
        this.notifications = notifications;
        this.users = users;
        this.sellers = sellers;
        for (NotificationSender sender : senderList) {
            if (senders.put(sender.channel(), sender) != null) {
                throw new IllegalStateException("More than one sender for channel " + sender.channel());
            }
        }
    }

    /**
     * Tells the buyer about an order status change (FR18): email if they have one, else SMS.
     * A failed send is recorded as FAILED and never thrown. REQUIRES_NEW because this runs after the
     * publisher's transaction has committed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyBuyer(OrderStatusChangedEvent event) {
        User buyer = users.findById(event.buyerId()).orElse(null);
        if (buyer == null) {
            log.warn("Buyer {} not found for order {}; not notifying", event.buyerId(), event.orderId());
            return;
        }
        String sellerName = sellers.findById(event.sellerProfileId())
                .map(SellerProfile::getBusinessName)
                .orElse("the seller");

        boolean hasEmail = buyer.getEmail() != null && !buyer.getEmail().isBlank();
        NotificationChannel channel = hasEmail ? NotificationChannel.EMAIL : NotificationChannel.SMS;
        String recipient = hasEmail ? buyer.getEmail().trim() : buyer.getPhone();
        String body = message(event, sellerName);
        String subject = "GharSe order #" + event.orderId() + " update";

        NotificationStatus status;
        NotificationSender sender = senders.get(channel);
        if (sender == null) {
            log.error("No sender registered for channel {}; order {} not notified", channel, event.orderId());
            status = NotificationStatus.FAILED;
        } else {
            try {
                sender.send(recipient, subject, body);
                status = NotificationStatus.SENT;
            } catch (RuntimeException e) {
                log.error("Notification for order {} via {} failed ({})",
                        event.orderId(), channel, e.getClass().getSimpleName());
                log.debug("Notification failure details", e);
                status = NotificationStatus.FAILED;
            }
        }
        notifications.save(new Notification(buyer.getId(), channel, TYPE_ORDER_STATUS_CHANGE, body, status));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> myNotifications(Long userId) {
        return notifications.findTop50ByUserIdOrderBySentAtDescIdDesc(userId)
                .stream().map(NotificationResponse::from).toList();
    }

    static String message(OrderStatusChangedEvent event, String sellerName) {
        String phrase = switch (event.newStatus()) {
            case CONFIRMED -> "has been confirmed";
            case IN_PRODUCTION -> "is now being prepared";
            case READY -> event.fulfilmentMode() == FulfilmentMode.PICKUP ? "is ready for pickup" : "is ready";
            case OUT_FOR_DELIVERY -> event.fulfilmentMode() == FulfilmentMode.POSTAL
                    ? "has been dispatched" : "is out for delivery";
            case DELIVERED -> "has been delivered";
        };
        return "Your order #" + event.orderId() + " from " + sellerName + " " + phrase + ".";
    }
}