package com.gharse.payment;

import com.gharse.order.FulfilmentMode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PaymentTest {

    private Payment newPayment() {
        return new Payment(1L, 2L, new BigDecimal("499.00"), FulfilmentMode.PICKUP, null);
    }

    @Test
    void newPaymentIsPendingWithoutRazorpayId() {
        Payment p = newPayment();
        assertEquals(PaymentStatus.PENDING, p.getStatus());
        assertNull(p.getRazorpayOrderId());
        assertNotNull(p.getCreatedAt());
        assertNull(p.getConfirmedAt());
    }

    @Test
    void rejectsBadConstructorArguments() {
        assertThrows(IllegalArgumentException.class,
                () -> new Payment(1L, 2L, BigDecimal.ZERO, FulfilmentMode.PICKUP, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Payment(1L, 2L, new BigDecimal("-5.00"), FulfilmentMode.PICKUP, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Payment(null, 2L, BigDecimal.TEN, FulfilmentMode.PICKUP, null));
        assertThrows(ArithmeticException.class,
                () -> new Payment(1L, 2L, new BigDecimal("10.005"), FulfilmentMode.PICKUP, null));
    }

    @Test
    void razorpayIdCanBeAttachedOnlyOnce() {
        Payment p = newPayment();
        p.attachRazorpayOrderId("order_a");
        assertEquals("order_a", p.getRazorpayOrderId());
        assertThrows(IllegalStateException.class, () -> p.attachRazorpayOrderId("order_b"));
    }

    @Test
    void markSuccessFromPendingSetsConfirmedAt() {
        Payment p = newPayment();
        LocalDateTime now = LocalDateTime.now();
        p.markSuccess(now);
        assertEquals(PaymentStatus.SUCCESS, p.getStatus());
        assertEquals(now, p.getConfirmedAt());
    }

    @Test
    void lateCaptureAfterFailedIsAccepted() {
        Payment p = newPayment();
        p.markFailed();
        p.markSuccess(LocalDateTime.now());
        assertEquals(PaymentStatus.SUCCESS, p.getStatus());
    }

    @Test
    void markSuccessTwiceIsRejected() {
        Payment p = newPayment();
        p.markSuccess(LocalDateTime.now());
        assertThrows(IllegalStateException.class, () -> p.markSuccess(LocalDateTime.now()));
    }

    @Test
    void markFailedOnlyFromPending() {
        Payment p = newPayment();
        p.markSuccess(LocalDateTime.now());
        assertThrows(IllegalStateException.class, p::markFailed);
    }

    @Test
    void refundWorksOnceFromAnyOtherState() {
        Payment p = newPayment();
        p.markRefunded();
        assertEquals(PaymentStatus.REFUNDED, p.getStatus());
        assertThrows(IllegalStateException.class, p::markRefunded);
    }

    @Test
    void paymentItemRejectsBadQuantity() {
        assertThrows(IllegalArgumentException.class,
                () -> new PaymentItem(1L, 1L, "Cake", new BigDecimal("100.00"), 0));
        assertEquals(2, new PaymentItem(1L, 1L, "Cake", new BigDecimal("100.00"), 2).getQuantity());
    }
}