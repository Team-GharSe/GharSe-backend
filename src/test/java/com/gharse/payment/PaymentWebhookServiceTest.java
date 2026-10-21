package com.gharse.payment;

import com.gharse.cart.Cart;
import com.gharse.cart.CartItem;
import com.gharse.cart.CartItemRepository;
import com.gharse.cart.CartRepository;
import com.gharse.catalog.FulfilmentType;
import com.gharse.catalog.Product;
import com.gharse.catalog.ProductRepository;
import com.gharse.order.FulfilmentMode;
import com.gharse.order.Order;
import com.gharse.order.OrderItem;
import com.gharse.order.OrderItemRepository;
import com.gharse.order.OrderRepository;
import com.gharse.order.OrderStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentWebhookServiceTest {

    private static final Long BUYER = 1L;
    private static final Long SELLER = 7L;
    private static final Long PAYMENT_ID = 77L;
    private static final String RZP_ORDER = "order_1";

    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentItemRepository paymentItems = mock(PaymentItemRepository.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderItemRepository orderItems = mock(OrderItemRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final CartRepository carts = mock(CartRepository.class);
    private final CartItemRepository cartItems = mock(CartItemRepository.class);

    private PaymentWebhookService service;
    private Payment payment;
    private Product cake;
    private Product custom;

    @BeforeEach
    void setUp() {
        service = new PaymentWebhookService(payments, paymentItems, orders, orderItems, products,
                carts, cartItems, mock(PlatformTransactionManager.class));

        payment = new Payment(BUYER, SELLER, new BigDecimal("250.00"), FulfilmentMode.PICKUP, null);
        ReflectionTestUtils.setField(payment, "paymentId", PAYMENT_ID);
        payment.attachRazorpayOrderId(RZP_ORDER);
        when(payments.findByRazorpayOrderIdForUpdate(RZP_ORDER)).thenReturn(Optional.of(payment));

        cake = product(5, "Cake", "100.00", FulfilmentType.READY_STOCK, 10);
        custom = product(6, "Custom Cake", "50.00", FulfilmentType.MADE_TO_ORDER, null);
        when(products.findById(5L)).thenReturn(Optional.of(cake));
        when(products.findById(6L)).thenReturn(Optional.of(custom));
        when(products.saveAndFlush(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        when(paymentItems.findByPaymentIdOrderByPaymentItemId(PAYMENT_ID)).thenReturn(List.of(
                new PaymentItem(PAYMENT_ID, 5L, "Cake", new BigDecimal("100.00"), 2),
                new PaymentItem(PAYMENT_ID, 6L, "Custom Cake", new BigDecimal("50.00"), 1)));

        when(orders.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            ReflectionTestUtils.setField(o, "id", 90L);
            return o;
        });
    }

    private static Product product(long id, String name, String price, FulfilmentType type, Integer inventory) {
        Product p = new Product();
        p.setId(id);
        p.setSellerProfileId(SELLER);
        p.setName(name);
        p.setPrice(new BigDecimal(price));
        p.setFulfilmentType(type);
        p.setInventoryQty(inventory);
        p.setActive(true);
        return p;
    }

    private static RazorpayWebhookEvent.PaymentEntity entity(Long amount, String currency, String orderId) {
        return new RazorpayWebhookEvent.PaymentEntity("pay_x", amount, currency, "captured", orderId, null, null);
    }

    private static RazorpayWebhookEvent.PaymentEntity captured() {
        return entity(25000L, "INR", RZP_ORDER);
    }

    private Cart lockedCart() {
        Cart cart = new Cart(BUYER);
        ReflectionTestUtils.setField(cart, "id", 10L);
        cart.lockTo(SELLER);
        when(carts.findByUserIdForUpdate(BUYER)).thenReturn(Optional.of(cart));
        return cart;
    }

    // ---------------- captured: booking ----------------

    @Test
    void capturedBooksOrderCopiesSnapshotDecrementsReadyStockAndMarksSuccess() {
        service.handleCaptured(captured());

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        assertNotNull(payment.getConfirmedAt());
        assertEquals(8, cake.getInventoryQty());
        assertNull(custom.getInventoryQty());

        ArgumentCaptor<Order> order = ArgumentCaptor.forClass(Order.class);
        verify(orders).save(order.capture());
        assertEquals(PAYMENT_ID, order.getValue().getPaymentId());
        assertEquals(BUYER, order.getValue().getBuyerId());
        assertEquals(SELLER, order.getValue().getSellerProfileId());
        assertEquals(FulfilmentMode.PICKUP, order.getValue().getFulfilmentModeSelected());
        assertEquals(OrderStatus.CONFIRMED, order.getValue().getStatus());

        ArgumentCaptor<OrderItem> items = ArgumentCaptor.forClass(OrderItem.class);
        verify(orderItems, times(2)).save(items.capture());
        assertEquals(90L, items.getAllValues().get(0).getOrderId());
        assertEquals(5L, items.getAllValues().get(0).getProductId());
        assertEquals("Cake", items.getAllValues().get(0).getProductNameSnapshot());
        assertEquals(new BigDecimal("100.00"), items.getAllValues().get(0).getPriceSnapshot());
        assertEquals(2, items.getAllValues().get(0).getQuantity());
    }

    @Test
    void lateCaptureAfterFailedIsBooked() {
        payment.markFailed();

        service.handleCaptured(captured());

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        verify(orders).save(any(Order.class));
    }

    @Test
    void insufficientStockStillBooksAndClampsToZero() {
        cake.setInventoryQty(1);

        service.handleCaptured(captured());

        assertEquals(0, cake.getInventoryQty());
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        verify(orders).save(any(Order.class));
    }

    // ---------------- captured: idempotency and guards ----------------

    @Test
    void alreadySuccessfulPaymentIsNoOp() {
        payment.markSuccess(LocalDateTime.now());

        service.handleCaptured(captured());

        verify(orders, never()).save(any(Order.class));
        verify(products, never()).saveAndFlush(any(Product.class));
        assertEquals(10, cake.getInventoryQty());
    }

    @Test
    void existingOrderForPaymentIsNoOp() {
        when(orders.findByPaymentId(PAYMENT_ID))
                .thenReturn(Optional.of(new Order(PAYMENT_ID, BUYER, SELLER, FulfilmentMode.PICKUP, null)));

        service.handleCaptured(captured());

        verify(orders, never()).save(any(Order.class));
        verify(products, never()).saveAndFlush(any(Product.class));
    }

    @Test
    void amountMismatchDoesNotBook() {
        service.handleCaptured(entity(100L, "INR", RZP_ORDER));

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        verify(orders, never()).save(any(Order.class));
        verify(products, never()).saveAndFlush(any(Product.class));
    }

    @Test
    void currencyMismatchDoesNotBook() {
        service.handleCaptured(entity(25000L, "USD", RZP_ORDER));

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        verify(orders, never()).save(any(Order.class));
    }

    @Test
    void unknownRazorpayOrderIsIgnored() {
        when(payments.findByRazorpayOrderIdForUpdate("order_unknown")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.handleCaptured(entity(25000L, "INR", "order_unknown")));

        verify(orders, never()).save(any(Order.class));
    }

    @Test
    void nullOrBlankOrderIdIsIgnored() {
        service.handleCaptured(null);
        service.handleCaptured(entity(25000L, "INR", null));
        service.handleCaptured(entity(25000L, "INR", "  "));

        verifyNoInteractions(payments);
    }

    @Test
    void refundedPaymentIsIgnored() {
        payment.markRefunded();

        service.handleCaptured(captured());

        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        verify(orders, never()).save(any(Order.class));
    }

    @Test
    void emptySnapshotIsNotBooked() {
        when(paymentItems.findByPaymentIdOrderByPaymentItemId(PAYMENT_ID)).thenReturn(List.of());

        service.handleCaptured(captured());

        assertEquals(PaymentStatus.PENDING, payment.getStatus());
        verify(orders, never()).save(any(Order.class));
    }

    // ---------------- captured: optimistic-lock retry ----------------

    @Test
    void optimisticConflictRetriesTheWholeTransactionThenSucceeds() {
        // fresh stock read on every attempt, like a real new transaction
        when(products.findById(5L)).thenAnswer(inv ->
                Optional.of(product(5, "Cake", "100.00", FulfilmentType.READY_STOCK, 10)));
        doThrow(new OptimisticLockingFailureException("conflict"))
                .doAnswer(inv -> inv.getArgument(0))
                .when(products).saveAndFlush(any(Product.class));

        service.handleCaptured(captured());

        verify(products, times(2)).saveAndFlush(any(Product.class));
        verify(orders, times(1)).save(any(Order.class));
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
    }

    @Test
    void optimisticConflictOnEveryAttemptRethrowsAndBooksNothing() {
        when(products.findById(5L)).thenAnswer(inv ->
                Optional.of(product(5, "Cake", "100.00", FulfilmentType.READY_STOCK, 10)));
        doThrow(new OptimisticLockingFailureException("conflict"))
                .when(products).saveAndFlush(any(Product.class));

        assertThrows(OptimisticLockingFailureException.class, () -> service.handleCaptured(captured()));

        verify(products, times(PaymentWebhookService.MAX_ATTEMPTS)).saveAndFlush(any(Product.class));
        verify(orders, never()).save(any(Order.class));
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
    }

    // ---------------- captured: cart clearing ----------------

    @Test
    void cartLinesAtOrBelowSnapshotAreDeletedAndLockReleased() {
        Cart cart = lockedCart();
        CartItem cakeLine = new CartItem(10L, 5L, 2);
        CartItem customLine = new CartItem(10L, 6L, 1);
        when(cartItems.findByCartIdAndProductId(10L, 5L)).thenReturn(Optional.of(cakeLine));
        when(cartItems.findByCartIdAndProductId(10L, 6L)).thenReturn(Optional.of(customLine));
        when(cartItems.countByCartId(10L)).thenReturn(0L);

        service.handleCaptured(captured());

        verify(cartItems).delete(cakeLine);
        verify(cartItems).delete(customLine);
        assertNull(cart.getSellerProfileId());
    }

    @Test
    void cartLineAboveSnapshotIsReducedAndLockKept() {
        Cart cart = lockedCart();
        CartItem cakeLine = new CartItem(10L, 5L, 5);
        when(cartItems.findByCartIdAndProductId(10L, 5L)).thenReturn(Optional.of(cakeLine));
        when(cartItems.countByCartId(10L)).thenReturn(1L);

        service.handleCaptured(captured());

        assertEquals(3, cakeLine.getQuantity());
        verify(cartItems, never()).delete(any(CartItem.class));
        assertEquals(SELLER, cart.getSellerProfileId());
    }

    @Test
    void missingCartDoesNotBlockBooking() {
        when(carts.findByUserIdForUpdate(BUYER)).thenReturn(Optional.empty());

        service.handleCaptured(captured());

        verify(orders).save(any(Order.class));
        verifyNoInteractions(cartItems);
    }

    // ---------------- failed ----------------

    @Test
    void failedEventMarksPendingPaymentFailed() {
        service.handleFailed(entity(25000L, "INR", RZP_ORDER));

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
    }

    @Test
    void failedEventAfterSuccessIsIgnored() {
        payment.markSuccess(LocalDateTime.now());

        service.handleFailed(entity(25000L, "INR", RZP_ORDER));

        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
    }

    @Test
    void failedEventForUnknownOrderOrNullEntityIsIgnored() {
        when(payments.findByRazorpayOrderIdForUpdate("order_unknown")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> service.handleFailed(entity(1L, "INR", "order_unknown")));
        assertDoesNotThrow(() -> service.handleFailed(null));
    }
}