package com.gharse.notification;

import com.gharse.order.FulfilmentMode;
import com.gharse.order.OrderStatus;
import com.gharse.order.OrderStatusChangedEvent;
import com.gharse.seller.SellerProfile;
import com.gharse.seller.SellerProfileRepository;
import com.gharse.user.User;
import com.gharse.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceTest {

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SellerProfileRepository sellers = mock(SellerProfileRepository.class);
    private final NotificationSender email = mock(NotificationSender.class);
    private final NotificationSender sms = mock(NotificationSender.class);
    private NotificationService service;

    @BeforeEach
    void setUp() {
        when(email.channel()).thenReturn(NotificationChannel.EMAIL);
        when(sms.channel()).thenReturn(NotificationChannel.SMS);
        service = new NotificationService(notifications, users, sellers, List.of(email, sms));

        SellerProfile asha = new SellerProfile();
        asha.setBusinessName("Asha Bakes");
        when(sellers.findById(3L)).thenReturn(Optional.of(asha));
    }

    private void buyer(String emailAddress) {
        User u = new User();
        ReflectionTestUtils.setField(u, "id", 1L);
        u.setName("Buyer");
        u.setPhone("9000000001");
        u.setEmail(emailAddress);
        when(users.findById(1L)).thenReturn(Optional.of(u));
    }

    private static OrderStatusChangedEvent event(OrderStatus previous, OrderStatus next, FulfilmentMode mode) {
        return new OrderStatusChangedEvent(12L, 1L, 3L, mode, previous, next);
    }

    private Notification savedRow() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void buyerWithEmailGetsEmail() {
        buyer("buyer@example.com");

        service.notifyBuyer(event(null, OrderStatus.CONFIRMED, FulfilmentMode.PICKUP));

        verify(email).send(eq("buyer@example.com"), anyString(), contains("#12"));
        verify(sms, never()).send(any(), any(), any());
        Notification row = savedRow();
        assertEquals(NotificationChannel.EMAIL, row.getChannel());
        assertEquals(NotificationStatus.SENT, row.getStatus());
        assertEquals(1L, row.getUserId());
        assertEquals("ORDER_STATUS_CHANGE", row.getType());
        assertTrue(row.getContent().contains("Asha Bakes"));
    }

    @Test
    void buyerWithoutEmailGetsSms() {
        buyer(null);

        service.notifyBuyer(event(null, OrderStatus.CONFIRMED, FulfilmentMode.PICKUP));

        verify(sms).send(eq("9000000001"), anyString(), anyString());
        verify(email, never()).send(any(), any(), any());
        assertEquals(NotificationChannel.SMS, savedRow().getChannel());
    }

    @Test
    void blankEmailFallsBackToSms() {
        buyer("   ");

        service.notifyBuyer(event(OrderStatus.CONFIRMED, OrderStatus.IN_PRODUCTION, FulfilmentMode.PICKUP));

        verify(sms).send(eq("9000000001"), anyString(), anyString());
        assertEquals(NotificationChannel.SMS, savedRow().getChannel());
    }

    @Test
    void senderFailureIsRecordedAsFailedAndNeverThrown() {
        buyer("buyer@example.com");
        doThrow(new RuntimeException("smtp down")).when(email).send(any(), any(), any());

        assertDoesNotThrow(() ->
                service.notifyBuyer(event(null, OrderStatus.CONFIRMED, FulfilmentMode.PICKUP)));

        assertEquals(NotificationStatus.FAILED, savedRow().getStatus());
    }

    @Test
    void missingSenderForChannelIsRecordedAsFailed() {
        service = new NotificationService(notifications, users, sellers, List.of(sms));
        buyer("buyer@example.com");

        service.notifyBuyer(event(null, OrderStatus.CONFIRMED, FulfilmentMode.PICKUP));

        Notification row = savedRow();
        assertEquals(NotificationChannel.EMAIL, row.getChannel());
        assertEquals(NotificationStatus.FAILED, row.getStatus());
    }

    @Test
    void unknownBuyerIsIgnored() {
        when(users.findById(1L)).thenReturn(Optional.empty());

        assertDoesNotThrow(() ->
                service.notifyBuyer(event(null, OrderStatus.CONFIRMED, FulfilmentMode.PICKUP)));

        verify(notifications, never()).save(any(Notification.class));
    }

    @Test
    void missingSellerFallsBackToGenericName() {
        buyer(null);
        when(sellers.findById(3L)).thenReturn(Optional.empty());

        service.notifyBuyer(event(null, OrderStatus.CONFIRMED, FulfilmentMode.PICKUP));

        assertTrue(savedRow().getContent().contains("the seller"));
    }

    @Test
    void messageTextFollowsStatusAndFulfilmentMode() {
        assertEquals("Your order #12 from Asha Bakes has been confirmed.",
                NotificationService.message(event(null, OrderStatus.CONFIRMED, FulfilmentMode.PICKUP), "Asha Bakes"));
        assertEquals("Your order #12 from Asha Bakes is now being prepared.",
                NotificationService.message(event(OrderStatus.CONFIRMED, OrderStatus.IN_PRODUCTION, FulfilmentMode.PICKUP), "Asha Bakes"));
        assertEquals("Your order #12 from Asha Bakes is ready for pickup.",
                NotificationService.message(event(OrderStatus.IN_PRODUCTION, OrderStatus.READY, FulfilmentMode.PICKUP), "Asha Bakes"));
        assertEquals("Your order #12 from Asha Bakes is ready.",
                NotificationService.message(event(OrderStatus.IN_PRODUCTION, OrderStatus.READY, FulfilmentMode.SELF_DELIVERY), "Asha Bakes"));
        assertEquals("Your order #12 from Asha Bakes is out for delivery.",
                NotificationService.message(event(OrderStatus.READY, OrderStatus.OUT_FOR_DELIVERY, FulfilmentMode.SELF_DELIVERY), "Asha Bakes"));
        assertEquals("Your order #12 from Asha Bakes has been dispatched.",
                NotificationService.message(event(OrderStatus.READY, OrderStatus.OUT_FOR_DELIVERY, FulfilmentMode.POSTAL), "Asha Bakes"));
        assertEquals("Your order #12 from Asha Bakes has been delivered.",
                NotificationService.message(event(OrderStatus.OUT_FOR_DELIVERY, OrderStatus.DELIVERED, FulfilmentMode.POSTAL), "Asha Bakes"));
    }

    @Test
    void myNotificationsKeepsRepositoryOrderAndMapsFields() {
        Notification newer = new Notification(1L, NotificationChannel.SMS, "ORDER_STATUS_CHANGE",
                "second", NotificationStatus.SENT);
        Notification older = new Notification(1L, NotificationChannel.EMAIL, "ORDER_STATUS_CHANGE",
                "first", NotificationStatus.FAILED);
        when(notifications.findTop50ByUserIdOrderBySentAtDescIdDesc(1L)).thenReturn(List.of(newer, older));

        List<NotificationResponse> result = service.myNotifications(1L);

        assertEquals(2, result.size());
        assertEquals("second", result.get(0).content());
        assertEquals(NotificationChannel.SMS, result.get(0).channel());
        assertEquals(NotificationStatus.FAILED, result.get(1).status());
        assertNotNull(result.get(0).sentAt());
    }
}