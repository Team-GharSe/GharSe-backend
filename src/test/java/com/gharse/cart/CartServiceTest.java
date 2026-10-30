package com.gharse.cart;

import com.gharse.catalog.EtaCalculator;
import com.gharse.catalog.Product;
import com.gharse.catalog.ProductRepository;
import com.gharse.common.exception.ConflictException;
import com.gharse.common.exception.ResourceNotFoundException;
import com.gharse.seller.SellerProfile;
import com.gharse.seller.SellerProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CartServiceTest {

    private static final long USER = 1L;
    private static final long CART_ID = 10L;

    private final CartRepository carts = mock(CartRepository.class);
    private final CartItemRepository items = mock(CartItemRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final SellerProfileRepository sellers = mock(SellerProfileRepository.class);
    private final EtaCalculator etaCalculator = mock(EtaCalculator.class);
    private CartService service;

    @BeforeEach
    void setUp() {
        service = new CartService(carts, items, products, sellers, etaCalculator);
        when(carts.save(any(Cart.class))).thenAnswer(inv -> {
            Cart c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", CART_ID);
            return c;
        });
    }

    private static Product product(long id, long sellerId, String price, boolean active) {
        Product p = new Product();
        p.setId(id);
        p.setSellerProfileId(sellerId);
        p.setName("Product " + id);
        p.setPrice(new BigDecimal(price));
        p.setActive(active);
        return p;
    }

    private Cart existingCart(Long lockedSeller) {
        Cart cart = new Cart(USER);
        ReflectionTestUtils.setField(cart, "id", CART_ID);
        if (lockedSeller != null) {
            cart.lockTo(lockedSeller);
        }
        when(carts.findByUserIdForUpdate(USER)).thenReturn(Optional.of(cart));
        return cart;
    }

    @Test
    void firstItemCreatesCartAndLocksToSeller() {
        when(products.findByIdAndActiveTrue(5L)).thenReturn(Optional.of(product(5, 7, "100.00", true)));
        when(carts.findByUserIdForUpdate(USER)).thenReturn(Optional.empty());

        service.addItem(USER, 5L, 2);

        ArgumentCaptor<Cart> cartCaptor = ArgumentCaptor.forClass(Cart.class);
        verify(carts).save(cartCaptor.capture());
        assertEquals(7L, cartCaptor.getValue().getSellerProfileId());
        ArgumentCaptor<CartItem> itemCaptor = ArgumentCaptor.forClass(CartItem.class);
        verify(items).save(itemCaptor.capture());
        assertEquals(2, itemCaptor.getValue().getQuantity());
        assertEquals(5L, itemCaptor.getValue().getProductId());
    }

    @Test
    void sameProductAgainIncreasesQuantity() {
        existingCart(7L);
        when(products.findByIdAndActiveTrue(5L)).thenReturn(Optional.of(product(5, 7, "100.00", true)));
        CartItem item = new CartItem(CART_ID, 5L, 3);
        when(items.findByCartIdAndProductId(CART_ID, 5L)).thenReturn(Optional.of(item));

        service.addItem(USER, 5L, 2);

        assertEquals(5, item.getQuantity());
        verify(items, never()).save(any(CartItem.class));
    }

    @Test
    void differentSellerIsRejectedWithSellerNameAndNothingIsSaved() {
        Cart cart = existingCart(7L);
        when(products.findByIdAndActiveTrue(6L)).thenReturn(Optional.of(product(6, 8, "50.00", true)));
        when(items.countByCartId(CART_ID)).thenReturn(2L);
        SellerProfile asha = new SellerProfile();
        asha.setBusinessName("Asha Bakes");
        when(sellers.findById(7L)).thenReturn(Optional.of(asha));

        ConflictException ex = assertThrows(ConflictException.class, () -> service.addItem(USER, 6L, 1));

        assertTrue(ex.getMessage().contains("Asha Bakes"));
        verify(items, never()).save(any(CartItem.class));
        assertEquals(7L, cart.getSellerProfileId());
    }

    @Test
    void staleLockOnEmptyCartDoesNotBlockAnotherSeller() {
        Cart cart = existingCart(7L);
        when(products.findByIdAndActiveTrue(6L)).thenReturn(Optional.of(product(6, 8, "50.00", true)));
        when(items.countByCartId(CART_ID)).thenReturn(0L);

        service.addItem(USER, 6L, 1);

        assertEquals(8L, cart.getSellerProfileId());
        verify(items).save(any(CartItem.class));
    }

    @Test
    void quantityAboveCapAfterMergeIsRejected() {
        existingCart(7L);
        when(products.findByIdAndActiveTrue(5L)).thenReturn(Optional.of(product(5, 7, "100.00", true)));
        when(items.findByCartIdAndProductId(CART_ID, 5L))
                .thenReturn(Optional.of(new CartItem(CART_ID, 5L, CartService.MAX_QUANTITY_PER_ITEM - 1)));

        assertThrows(ConflictException.class, () -> service.addItem(USER, 5L, 2));
    }

    @Test
    void inactiveOrMissingProductIs404() {
        when(products.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.addItem(USER, 99L, 1));
        verify(carts, never()).save(any(Cart.class));
    }

    @Test
    void removingLastItemReleasesLock() {
        Cart cart = existingCart(7L);
        CartItem item = new CartItem(CART_ID, 5L, 1);
        when(items.findByCartIdAndProductId(CART_ID, 5L)).thenReturn(Optional.of(item));
        when(items.countByCartId(CART_ID)).thenReturn(0L);

        service.removeItem(USER, 5L);

        verify(items).delete(item);
        assertNull(cart.getSellerProfileId());
    }

    @Test
    void removingNonLastItemKeepsLock() {
        Cart cart = existingCart(7L);
        CartItem item = new CartItem(CART_ID, 5L, 1);
        when(items.findByCartIdAndProductId(CART_ID, 5L)).thenReturn(Optional.of(item));
        when(items.countByCartId(CART_ID)).thenReturn(1L);

        service.removeItem(USER, 5L);

        assertEquals(7L, cart.getSellerProfileId());
    }

    @Test
    void clearDeletesItemsAndReleasesLock() {
        Cart cart = existingCart(7L);

        CartView view = service.clear(USER);

        verify(items).deleteByCartId(CART_ID);
        assertNull(cart.getSellerProfileId());
        assertTrue(view.items().isEmpty());
    }

    @Test
    void clearWithNoCartIsHarmless() {
        when(carts.findByUserIdForUpdate(USER)).thenReturn(Optional.empty());
        assertTrue(service.clear(USER).items().isEmpty());
        verify(items, never()).deleteByCartId(any());
    }

    @Test
    void updateMissingItemIs404() {
        existingCart(7L);
        when(items.findByCartIdAndProductId(CART_ID, 5L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.updateQuantity(USER, 5L, 3));
    }

    @Test
    void updateSetsNewQuantity() {
        existingCart(7L);
        CartItem item = new CartItem(CART_ID, 5L, 1);
        when(items.findByCartIdAndProductId(CART_ID, 5L)).thenReturn(Optional.of(item));

        service.updateQuantity(USER, 5L, 4);

        assertEquals(4, item.getQuantity());
    }

    @Test
    void viewComputesTotalsOnServerAndExcludesInactiveFromSubtotal() {
        Cart cart = existingCart(7L);
        when(carts.findByUserId(USER)).thenReturn(Optional.of(cart));
        when(items.findByCartIdOrderByIdAsc(CART_ID)).thenReturn(List.of(
                new CartItem(CART_ID, 5L, 2), new CartItem(CART_ID, 6L, 1)));
        when(products.findAllById(any())).thenReturn(List.of(
                product(5, 7, "100.00", true), product(6, 7, "50.00", false)));

        CartView view = service.getCart(USER);

        assertEquals(2, view.items().size());
        assertEquals(new BigDecimal("200.00"), view.items().get(0).lineTotal());
        assertFalse(view.items().get(1).available());
        assertEquals(new BigDecimal("200.00"), view.subtotal());
    }

    @Test
    void viewOfUserWithoutCartIsEmpty() {
        when(carts.findByUserId(USER)).thenReturn(Optional.empty());
        CartView view = service.getCart(USER);
        assertTrue(view.items().isEmpty());
        assertEquals(new BigDecimal("0.00"), view.subtotal());
    }
}