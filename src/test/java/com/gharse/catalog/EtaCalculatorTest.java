package com.gharse.catalog;

import com.gharse.order.OrderItemRepository;
import com.gharse.order.OrderStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EtaCalculatorTest {

    private static final Long SELLER = 7L;
    private static final List<OrderStatus> OPEN = List.of(OrderStatus.CONFIRMED, OrderStatus.IN_PRODUCTION);

    private final OrderItemRepository orderItems = mock(OrderItemRepository.class);
    private final EtaCalculator calculator = new EtaCalculator(orderItems);

    private void queueOf(long minutes) {
        when(orderItems.sumBacklogMinutes(SELLER, OPEN)).thenReturn(minutes);
    }

    private static Product madeToOrder(Integer minutesPerUnit) {
        Product p = new Product();
        p.setId(5L);
        p.setSellerProfileId(SELLER);
        p.setFulfilmentType(FulfilmentType.MADE_TO_ORDER);
        p.setProductionTimeMinutesPerUnit(minutesPerUnit);
        return p;
    }

    @Test
    void queuesBehindTheSellersWholeBacklog() {
        queueOf(2400);   // 2400 + 1 x 120 = 2520 minutes -> 1.75 days -> 2
        assertEquals(LocalDate.now().plusDays(2), calculator.estimatedReadyDate(madeToOrder(120), 1));
    }

    @Test
    void emptyQueueUsesOnlyThisQuantity() {
        queueOf(0);      // 3 x 120 = 360 minutes -> 1
        assertEquals(LocalDate.now().plusDays(1), calculator.estimatedReadyDate(madeToOrder(120), 3));
    }

    @Test
    void exactDayBoundaryDoesNotRoundUp() {
        queueOf(1320);   // 1320 + 120 = 1440 minutes = exactly 1 day
        assertEquals(LocalDate.now().plusDays(1), calculator.estimatedReadyDate(madeToOrder(120), 1));
    }

    @Test
    void oneMinuteOverTheBoundaryRoundsUp() {
        queueOf(1321);   // 1441 minutes -> 2
        assertEquals(LocalDate.now().plusDays(2), calculator.estimatedReadyDate(madeToOrder(120), 1));
    }

    @Test
    void missingMinutesPerUnitCountsAsZero() {
        queueOf(1500);   // only the backlog counts: 1500 minutes -> 2
        assertEquals(LocalDate.now().plusDays(2), calculator.estimatedReadyDate(madeToOrder(null), 5));
    }

    @Test
    void emptyQueueAndZeroMinutesIsToday() {
        queueOf(0);
        assertEquals(LocalDate.now(), calculator.estimatedReadyDate(madeToOrder(0), 1));
    }

    @Test
    void quantityBelowOneIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> calculator.estimatedReadyDate(madeToOrder(120), 0));
        assertThrows(IllegalArgumentException.class, () -> calculator.estimatedReadyDate(madeToOrder(120), -1));
    }

    @Test
    void queueClearDateUsesOnlyTheBacklog() {
        queueOf(2400);
        assertEquals(LocalDate.now().plusDays(2), calculator.queueClearDate(SELLER));
        queueOf(1440);
        assertEquals(LocalDate.now().plusDays(1), calculator.queueClearDate(SELLER));
        queueOf(0);
        assertEquals(LocalDate.now(), calculator.queueClearDate(SELLER));
    }

    @Test
    void asksForTheProductsSellerWithBothOpenStatuses() {
        queueOf(100);

        calculator.estimatedReadyDate(madeToOrder(120), 1);

        verify(orderItems).sumBacklogMinutes(SELLER, OPEN);
    }
}