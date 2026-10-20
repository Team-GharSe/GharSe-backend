package com.gharse.payment;

import com.gharse.cart.Cart;
import com.gharse.cart.CartItem;
import com.gharse.cart.CartItemRepository;
import com.gharse.cart.CartRepository;
import com.gharse.catalog.FulfilmentType;
import com.gharse.catalog.Product;
import com.gharse.catalog.ProductRepository;
import com.gharse.common.exception.BadRequestException;
import com.gharse.common.exception.ConflictException;
import com.gharse.order.FulfilmentMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentServiceTest {

    private static final long BUYER = 1L;
    private static final long CART_ID = 10L;
    private static final long PAYMENT_ID = 77L;

    private final PaymentRepository payments = mock(PaymentRepository.class);
    private final PaymentItemRepository paymentItems = mock(PaymentItemRepository.class);
    private final CartRepository carts = mock(CartRepository.class);
    private final CartItemRepository cartItems = mock(CartItemRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final RazorpayService razorpay = mock(RazorpayService.class);
    private PaymentService service;
    private Payment saved;

    @BeforeEach
    void setUp() {
        service = new PaymentService(payments, paymentItems, carts, cartItems, products, razorpay,
                mock(PlatformTransactionManager.class), "rzp_test_key");
        when(payments.save(any(Payment.class))).thenAnswer(inv -> {
            saved = inv.getArgument(0);
            ReflectionTestUtils.setField(saved, "paymentId", PAYMENT_ID);
            return saved;
        });
        when(payments.findById(PAYMENT_ID)).thenAnswer(inv -> Optional.ofNullable(saved));
    }

    private void cartWith(List<CartItem> rows, Product... ps) {
        Cart cart = new Cart(BUYER);
        ReflectionTestUtils.setField(cart, "id", CART_ID);
        when(carts.findByUserIdForUpdate(BUYER)).thenReturn(Optional.of(cart));
        when(cartItems.findByCartIdOrderByIdAsc(CART_ID)).thenReturn(rows);
        when(products.findAllById(any())).thenReturn(List.of(ps));
    }

    private static CartItem row(long productId, int qty) {
        return new CartItem(CART_ID, productId, qty);
    }

    private static Product product(long id, long sellerId, String name, String price,
                                   FulfilmentType type, Integer inventory) {
        Product p = new Product();
        p.setId(id);
        p.setSellerProfileId(sellerId);
        p.setName(name);
        p.setPrice(new BigDecimal(price));
        p.setFulfilmentType(type);
        p.setInventoryQty(inventory);
        p.setActive(true);
        p.setSupportsPickup(true);
        return p;
    }

    @Test
    void createsPendingPaymentSnapshotsItemsAndReturnsRazorpayDetails() {
        cartWith(List.of(row(5, 2), row(6, 1)),
                product(5, 7, "Cake", "100.00", FulfilmentType.READY_STOCK, 10),
                product(6, 7, "Custom Cake", "50.00", FulfilmentType.MADE_TO_ORDER, null));
        when(razorpay.createOrder(25000L, "pay_77", Map.of("internal_payment_id", "77")))
                .thenReturn(new RazorpayOrderResponse("order_abc", 25000, "INR", "created"));

        CheckoutInitiateResponse r = service.initiate(BUYER, FulfilmentMode.PICKUP, null);

        assertEquals(Long.valueOf(PAYMENT_ID), r.paymentId());
        assertEquals("order_abc", r.razorpayOrderId());
        assertEquals("rzp_test_key", r.razorpayKeyId());
        assertEquals(25000L, r.amount());
        assertEquals("INR", r.currency());

        assertEquals(new BigDecimal("250.00"), saved.getAmountTotal());
        assertEquals(7L, saved.getSellerProfileId());
        assertEquals(FulfilmentMode.PICKUP, saved.getFulfilmentModeSelected());
        assertNull(saved.getDeliveryAddressSnapshot());
        assertEquals(PaymentStatus.PENDING, saved.getStatus());
        assertEquals("order_abc", saved.getRazorpayOrderId());

        ArgumentCaptor<PaymentItem> items = ArgumentCaptor.forClass(PaymentItem.class);
        verify(paymentItems, times(2)).save(items.capture());
        assertEquals("Cake", items.getAllValues().get(0).getProductNameSnapshot());
        assertEquals(new BigDecimal("100.00"), items.getAllValues().get(0).getPriceSnapshot());
        assertEquals(2, items.getAllValues().get(0).getQuantity());
    }

    @Test
    void razorpayFailureMarksPaymentFailedAndRethrows() {
        cartWith(List.of(row(5, 1)), product(5, 7, "Cake", "100.00", FulfilmentType.READY_STOCK, 10));
        when(razorpay.createOrder(anyLong(), anyString(), anyMap())).thenThrow(new RazorpayException("down"));

        assertThrows(RazorpayException.class, () -> service.initiate(BUYER, FulfilmentMode.PICKUP, null));

        assertEquals(PaymentStatus.FAILED, saved.getStatus());
        assertNull(saved.getRazorpayOrderId());
    }

    @Test
    void missingCartIsBadRequest() {
        when(carts.findByUserIdForUpdate(BUYER)).thenReturn(Optional.empty());
        assertThrows(BadRequestException.class, () -> service.initiate(BUYER, FulfilmentMode.PICKUP, null));
        verifyNoInteractions(razorpay);
    }

    @Test
    void emptyCartIsBadRequest() {
        cartWith(List.of());
        assertThrows(BadRequestException.class, () -> service.initiate(BUYER, FulfilmentMode.PICKUP, null));
        verify(payments, never()).save(any(Payment.class));
    }

    @Test
    void inactiveProductIsConflict() {
        Product cake = product(5, 7, "Cake", "100.00", FulfilmentType.READY_STOCK, 10);
        cake.setActive(false);
        cartWith(List.of(row(5, 1)), cake);

        assertThrows(ConflictException.class, () -> service.initiate(BUYER, FulfilmentMode.PICKUP, null));
        verify(payments, never()).save(any(Payment.class));
        verifyNoInteractions(razorpay);
    }

    @Test
    void insufficientStockIsConflictNamingTheProduct() {
        cartWith(List.of(row(5, 2)), product(5, 7, "Cake", "100.00", FulfilmentType.READY_STOCK, 1));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.initiate(BUYER, FulfilmentMode.PICKUP, null));
        assertTrue(ex.getMessage().contains("Cake"));
        verify(payments, never()).save(any(Payment.class));
    }

    @Test
    void itemsFromTwoSellersAreConflict() {
        cartWith(List.of(row(5, 1), row(6, 1)),
                product(5, 7, "Cake", "100.00", FulfilmentType.READY_STOCK, 10),
                product(6, 8, "Blouse", "500.00", FulfilmentType.READY_STOCK, 10));

        assertThrows(ConflictException.class, () -> service.initiate(BUYER, FulfilmentMode.PICKUP, null));
        verify(payments, never()).save(any(Payment.class));
    }

    @Test
    void modeNotSupportedByProductIsBadRequest() {
        Product cake = product(5, 7, "Cake", "100.00", FulfilmentType.READY_STOCK, 10);
        cake.setSupportsPickup(false);
        cartWith(List.of(row(5, 1)), cake);

        assertThrows(BadRequestException.class, () -> service.initiate(BUYER, FulfilmentMode.PICKUP, null));
        verify(payments, never()).save(any(Payment.class));
    }

    @Test
    void postalNeedsAnAddress() {
        Product cake = product(5, 7, "Cake", "100.00", FulfilmentType.READY_STOCK, 10);
        cake.setSupportsPostal(true);
        cartWith(List.of(row(5, 1)), cake);

        assertThrows(BadRequestException.class, () -> service.initiate(BUYER, FulfilmentMode.POSTAL, "   "));
        verify(payments, never()).save(any(Payment.class));
    }

    @Test
    void postalAddressIsTrimmedAndStored() {
        Product cake = product(5, 7, "Cake", "100.00", FulfilmentType.READY_STOCK, 10);
        cake.setSupportsPostal(true);
        cartWith(List.of(row(5, 1)), cake);
        when(razorpay.createOrder(anyLong(), anyString(), anyMap()))
                .thenReturn(new RazorpayOrderResponse("order_x", 10000, "INR", "created"));

        service.initiate(BUYER, FulfilmentMode.POSTAL, "  12 Main St  ");

        assertEquals("12 Main St", saved.getDeliveryAddressSnapshot());
    }

    @Test
    void pickupIgnoresAnyAddress() {
        cartWith(List.of(row(5, 1)), product(5, 7, "Cake", "100.00", FulfilmentType.READY_STOCK, 10));
        when(razorpay.createOrder(anyLong(), anyString(), anyMap()))
                .thenReturn(new RazorpayOrderResponse("order_x", 10000, "INR", "created"));

        service.initiate(BUYER, FulfilmentMode.PICKUP, "should be ignored");

        assertNull(saved.getDeliveryAddressSnapshot());
    }

    @Test
    void zeroTotalIsBadRequest() {
        cartWith(List.of(row(5, 1)), product(5, 7, "Freebie", "0.00", FulfilmentType.READY_STOCK, 10));

        assertThrows(BadRequestException.class, () -> service.initiate(BUYER, FulfilmentMode.PICKUP, null));
        verify(payments, never()).save(any(Payment.class));
    }
}