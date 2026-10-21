package com.gharse.payment;

import com.gharse.common.exception.ResourceNotFoundException;
import com.gharse.order.FulfilmentMode;
import com.gharse.order.Order;
import com.gharse.order.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentStatusServiceTest {

    private static final long BUYER = 1L;
    private static final long PAYMENT_ID = 5L;

    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private PaymentStatusService service;

    @BeforeEach
    void setUp() {
        service = new PaymentStatusService(payments, orders);
    }

    private Payment payment() {
        Payment p = new Payment(BUYER, 2L, new BigDecimal("200.00"), FulfilmentMode.PICKUP, null);
        ReflectionTestUtils.setField(p, "paymentId", PAYMENT_ID);
        when(payments.findByPaymentIdAndBuyerId(PAYMENT_ID, BUYER)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void ownerSeesPendingStatusWithNoOrderYet() {
        payment();

        PaymentStatusResponse r = service.getStatus(BUYER, PAYMENT_ID);

        assertEquals(PAYMENT_ID, r.paymentId());
        assertEquals(PaymentStatus.PENDING, r.status());
        assertNull(r.orderId());
        verify(payments).findByPaymentIdAndBuyerId(PAYMENT_ID, BUYER);
        verifyNoInteractions(orders);
    }

    @Test
    void reflectsSuccessAndFailedStatuses() {
        Payment p = payment();
        p.markSuccess(LocalDateTime.now());
        assertEquals(PaymentStatus.SUCCESS, service.getStatus(BUYER, PAYMENT_ID).status());

        Payment failed = new Payment(BUYER, 2L, new BigDecimal("50.00"), FulfilmentMode.PICKUP, null);
        ReflectionTestUtils.setField(failed, "paymentId", 6L);
        failed.markFailed();
        when(payments.findByPaymentIdAndBuyerId(6L, BUYER)).thenReturn(Optional.of(failed));
        assertEquals(PaymentStatus.FAILED, service.getStatus(BUYER, 6L).status());
    }

    @Test
    void successfulPaymentIncludesTheOrderId() {
        Payment p = payment();
        p.markSuccess(LocalDateTime.now());
        Order order = new Order(PAYMENT_ID, BUYER, 2L, FulfilmentMode.PICKUP, null);
        ReflectionTestUtils.setField(order, "id", 9L);
        when(orders.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(order));

        PaymentStatusResponse r = service.getStatus(BUYER, PAYMENT_ID);

        assertEquals(PaymentStatus.SUCCESS, r.status());
        assertEquals(9L, r.orderId());
    }

    @Test
    void successWithoutAnOrderRowReturnsNullOrderId() {
        Payment p = payment();
        p.markSuccess(LocalDateTime.now());

        assertNull(service.getStatus(BUYER, PAYMENT_ID).orderId());
    }

    @Test
    void anotherBuyersOrMissingPaymentIs404() {
        when(payments.findByPaymentIdAndBuyerId(PAYMENT_ID, 99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.getStatus(99L, PAYMENT_ID));
    }
}