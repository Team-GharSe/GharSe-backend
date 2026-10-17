package com.gharse.order;

import com.gharse.auth.AuthenticatedUser;
import com.gharse.common.exception.ConflictException;
import com.gharse.common.exception.ResourceNotFoundException;
import com.gharse.common.exception.UnauthorizedException;
import com.gharse.seller.SellerProfile;
import com.gharse.seller.SellerProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderServiceTest {
    private final AuthenticatedUser buyer = new AuthenticatedUser(20L, "BUYER");
    private final AuthenticatedUser seller = new AuthenticatedUser(30L, "SELLER");
    private OrderRepository orders;
    private OrderItemRepository items;
    private SellerProfileRepository sellers;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        items = mock(OrderItemRepository.class);
        sellers = mock(SellerProfileRepository.class);
        service = new OrderService(orders, items, sellers);
    }

    private Order readableOrder() {
        Order order = mock(Order.class);
        when(order.getId()).thenReturn(10L);
        when(order.getBuyerId()).thenReturn(20L);
        when(order.getSellerProfileId()).thenReturn(70L);
        when(order.getStatus()).thenReturn(OrderStatus.CONFIRMED);
        when(orders.findById(10L)).thenReturn(Optional.of(order));
        when(items.findByOrderIdOrderByIdAsc(10L)).thenReturn(List.of());
        return order;
    }

    private void owningSeller() {
        SellerProfile profile = mock(SellerProfile.class);
        when(profile.getId()).thenReturn(70L);
        when(profile.getUserId()).thenReturn(30L);
        when(sellers.findByUserId(30L)).thenReturn(Optional.of(profile));
        when(sellers.findById(70L)).thenReturn(Optional.of(profile));
    }

    @Test
    void buyerCanReadOwnOrderWithoutSellerLookup() {
        readableOrder();
        assertEquals(10L, service.getOrder(buyer, 10L).orderId());
        verifyNoInteractions(sellers);
    }

    @Test
    void owningSellerIsAuthorizedUsingProfileUserId() {
        readableOrder();
        owningSeller();
        assertEquals(10L, service.getOrder(seller, 10L).orderId());
        verify(sellers).findById(70L);
    }

    @Test
    void matchingSellerProfileNumberDoesNotGrantOwnership() {
        readableOrder();
        owningSeller();
        var impostor = new AuthenticatedUser(70L, "SELLER");
        assertThrows(ResourceNotFoundException.class, () -> service.getOrder(impostor, 10L));
        verifyNoInteractions(items);
    }

    @Test
    void unrelatedBuyerCannotReadOrder() {
        readableOrder();
        assertThrows(ResourceNotFoundException.class,
            () -> service.getOrder(new AuthenticatedUser(999L, "BUYER"), 10L));
        verifyNoInteractions(items);
    }

    @Test
    void sellerRoleAloneDoesNotGrantAccess() {
        readableOrder();
        assertThrows(ResourceNotFoundException.class, () -> service.getOrder(seller, 10L));
    }

    @Test
    void adminHasNoImplicitOrderAccess() {
        assertThrows(AccessDeniedException.class,
            () -> service.getOrder(new AuthenticatedUser(1L, "ADMIN"), 10L));
        verifyNoInteractions(orders, items, sellers);
    }

    @Test
    void missingOrderReturnsNotFound() {
        when(orders.findById(10L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getOrder(buyer, 10L));
    }

    @Test
    void mineQueriesOnlyVerifiedBuyerId() {
        when(orders.findByBuyerIdOrderByCreatedAtDescIdDesc(20L)).thenReturn(List.of());
        assertTrue(service.myOrders(buyer).isEmpty());
        verify(orders).findByBuyerIdOrderByCreatedAtDescIdDesc(20L);
    }

    @Test
    void sellerCanReadTheirOwnHistoricPurchases() {
        when(orders.findByBuyerIdOrderByCreatedAtDescIdDesc(30L)).thenReturn(List.of());
        service.myOrders(seller);
        verify(orders).findByBuyerIdOrderByCreatedAtDescIdDesc(30L);
    }

    @Test
    void snapshotNameAndPriceAreReturnedAsStored() {
        Order order = readableOrder();
        OrderItem item = new OrderItem(10L, 100L, "Original name", new BigDecimal("75.50"), 2);
        when(items.findByOrderIdOrderByIdAsc(10L)).thenReturn(List.of(item));
        var result = service.getOrder(buyer, order.getId()).items().get(0);
        assertEquals("Original name", result.productNameSnapshot());
        assertEquals(new BigDecimal("75.50"), result.priceSnapshot());
        assertEquals(2, result.quantity());
    }

    @Test
    void sellerListingResolvesUserToProfileAndFilters() {
        owningSeller();
        when(orders.findBySellerProfileIdAndStatusOrderByCreatedAtDescIdDesc(70L, OrderStatus.READY))
            .thenReturn(List.of());
        assertTrue(service.sellerOrders(seller, OrderStatus.READY).isEmpty());
        verify(orders).findBySellerProfileIdAndStatusOrderByCreatedAtDescIdDesc(70L, OrderStatus.READY);
        verify(orders, never()).findBySellerProfileIdOrderByCreatedAtDescIdDesc(any());
    }

    @Test
    void missingStatusFilterListsOnlyResolvedSeller() {
        owningSeller();
        when(orders.findBySellerProfileIdOrderByCreatedAtDescIdDesc(70L)).thenReturn(List.of());
        service.sellerOrders(seller, null);
        verify(orders).findBySellerProfileIdOrderByCreatedAtDescIdDesc(70L);
    }

    @Test
    void buyerCannotUseSellerListing() {
        assertThrows(AccessDeniedException.class, () -> service.sellerOrders(buyer, null));
        verifyNoInteractions(orders, sellers);
    }

    @Test
    void sellerMustHaveActualProfile() {
        when(sellers.findByUserId(30L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.sellerOrders(seller, null));
        verifyNoInteractions(orders);
    }

    private Order lockableOrder() {
        owningSeller();
        Order order = new Order(1L, 20L, 70L, FulfilmentMode.PICKUP, null);
        when(orders.findOwnedForUpdate(10L, 70L)).thenReturn(Optional.of(order));
        when(orders.saveAndFlush(order)).thenReturn(order);
        when(items.findByOrderIdOrderByIdAsc(null)).thenReturn(List.of());
        return order;
    }

    @Test
    void sellerCanAdvanceExactlyOneStep() {
        Order order = lockableOrder();
        assertEquals(OrderStatus.IN_PRODUCTION,
            service.updateStatus(seller, 10L, OrderStatus.IN_PRODUCTION).status());
        verify(orders).findOwnedForUpdate(10L, 70L);
        verify(orders).saveAndFlush(order);
    }

    @Test
    void serviceRejectsSkipAndDoesNotSave() {
        Order order = lockableOrder();
        assertThrows(ConflictException.class, () -> service.updateStatus(seller, 10L, OrderStatus.READY));
        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
        verify(orders, never()).saveAndFlush(any());
    }

    @Test
    void buyerCannotUpdateEvenTheirOwnOrder() {
        assertThrows(AccessDeniedException.class,
            () -> service.updateStatus(buyer, 10L, OrderStatus.IN_PRODUCTION));
        verifyNoInteractions(orders, sellers);
    }

    @Test
    void otherSellerCannotLockOrUpdateTargetOrder() {
        owningSeller();
        when(orders.findOwnedForUpdate(10L, 70L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
            () -> service.updateStatus(seller, 10L, OrderStatus.IN_PRODUCTION));
        verify(orders, never()).saveAndFlush(any());
    }

    static Stream<Arguments> illegalPairs() {
        return OrderStatusValidatorTest.allTransitions().filter(a -> {
            Object[] pair = a.get();
            int from = ((OrderStatus) pair[0]).ordinal();
            int to = ((OrderStatus) pair[1]).ordinal();
            return to != from + 1;
        });
    }

    @ParameterizedTest(name = "service rejects {0} -> {1}")
    @MethodSource("illegalPairs")
    void everyIllegalTransitionIsRejectedThroughService(OrderStatus from, OrderStatus to) {
        Order order = lockableOrder();
        while (order.getStatus() != from) {
            order.transitionTo(OrderStatus.values()[order.getStatus().ordinal() + 1]);
        }
        assertThrows(ConflictException.class, () -> service.updateStatus(seller, 10L, to));
        assertEquals(from, order.getStatus());
        verify(orders, never()).saveAndFlush(any());
    }

    @Test
    void missingIdentityFailsClosed() {
        assertThrows(UnauthorizedException.class, () -> service.myOrders(null));
        assertThrows(UnauthorizedException.class, () -> service.myOrders(new AuthenticatedUser(null, "BUYER")));
        verifyNoInteractions(orders);
    }
}
