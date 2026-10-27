package com.gharse.order;

import com.gharse.auth.AuthenticatedUser;
import com.gharse.common.exception.ResourceNotFoundException;
import com.gharse.seller.SellerProfile;
import com.gharse.seller.SellerProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderServiceEventTest {

    private static final Long ORDER_ID = 10L;
    private static final Long SELLER_PROFILE_ID = 3L;
    private static final AuthenticatedUser SELLER_USER = new AuthenticatedUser(5L, "SELLER");

    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderItemRepository items = mock(OrderItemRepository.class);
    private final SellerProfileRepository sellers = mock(SellerProfileRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private Order order;

    @BeforeEach
    void setUp() {
        order = new Order(77L, 1L, SELLER_PROFILE_ID, FulfilmentMode.PICKUP, null);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        ReflectionTestUtils.setField(order, "updatedAt", LocalDateTime.now());

        SellerProfile seller = new SellerProfile();
        ReflectionTestUtils.setField(seller, "id", SELLER_PROFILE_ID);
        when(sellers.findByUserId(5L)).thenReturn(Optional.of(seller));
        when(orders.findOwnedForUpdate(ORDER_ID, SELLER_PROFILE_ID)).thenReturn(Optional.of(order));
        when(orders.saveAndFlush(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
        when(items.findByOrderIdOrderByIdAsc(ORDER_ID)).thenReturn(List.of());
    }

    @Test
    void legalTransitionPublishesEventWithPreviousAndNewStatus() {
        OrderService service = new OrderService(orders, items, sellers, events);

        service.updateStatus(SELLER_USER, ORDER_ID, OrderStatus.IN_PRODUCTION);

        ArgumentCaptor<Object> published = ArgumentCaptor.forClass(Object.class);
        verify(events, times(1)).publishEvent(published.capture());
        OrderStatusChangedEvent event = assertInstanceOf(OrderStatusChangedEvent.class, published.getValue());
        assertEquals(ORDER_ID, event.orderId());
        assertEquals(OrderStatus.CONFIRMED, event.previousStatus());
        assertEquals(OrderStatus.IN_PRODUCTION, event.newStatus());
    }

    @Test
    void illegalTransitionPublishesNothing() {
        OrderService service = new OrderService(orders, items, sellers, events);

        assertThrows(RuntimeException.class,
                () -> service.updateStatus(SELLER_USER, ORDER_ID, OrderStatus.READY)); // skips IN_PRODUCTION

        verifyNoInteractions(events);
    }

    @Test
    void legacyThreeArgumentConstructorStillWorksAndPublishesNothing() {
        OrderService service = new OrderService(orders, items, sellers);

        service.updateStatus(SELLER_USER, ORDER_ID, OrderStatus.IN_PRODUCTION);

        assertEquals(OrderStatus.IN_PRODUCTION, order.getStatus());
    }

    @Test
    void orderNotOwnedBySellerPublishesNothing() {
        OrderService service = new OrderService(orders, items, sellers, events);
        when(orders.findOwnedForUpdate(99L, SELLER_PROFILE_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.updateStatus(SELLER_USER, 99L, OrderStatus.IN_PRODUCTION));

        verifyNoInteractions(events);
    }
}