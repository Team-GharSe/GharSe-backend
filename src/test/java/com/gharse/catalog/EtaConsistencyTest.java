package com.gharse.catalog;

import com.gharse.cart.Cart;
import com.gharse.cart.CartItem;
import com.gharse.cart.CartItemRepository;
import com.gharse.cart.CartRepository;
import com.gharse.cart.CartService;
import com.gharse.order.OrderItemRepository;
import com.gharse.order.OrderStatus;
import com.gharse.seller.SellerProfileRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EtaConsistencyTest {

    @Test
    void productPageAndCartShowTheSameDateForTheSameQuantity() {
        OrderItemRepository orderItems = mock(OrderItemRepository.class);
        when(orderItems.sumBacklogMinutes(7L, List.of(OrderStatus.CONFIRMED, OrderStatus.IN_PRODUCTION)))
                .thenReturn(2400L);
        EtaCalculator calculator = new EtaCalculator(orderItems);

        Product cake = new Product();
        cake.setId(5L);
        cake.setSellerProfileId(7L);
        cake.setName("Custom Cake");
        cake.setPrice(new BigDecimal("800.00"));
        cake.setFulfilmentType(FulfilmentType.MADE_TO_ORDER);
        cake.setProductionTimeMinutesPerUnit(120);
        cake.setActive(true);

        ProductRepository products = mock(ProductRepository.class);
        when(products.findByIdAndActiveTrue(5L)).thenReturn(Optional.of(cake));
        when(products.findAllById(any())).thenReturn(List.of(cake));

        ProductQueryService catalog = new ProductQueryService(products, mock(ProductImageRepository.class),
                mock(CategoryRepository.class), mock(SellerProfileRepository.class), orderItems, calculator);

        CartRepository carts = mock(CartRepository.class);
        CartItemRepository items = mock(CartItemRepository.class);
        Cart cart = new Cart(1L);
        ReflectionTestUtils.setField(cart, "id", 10L);
        when(carts.findByUserId(1L)).thenReturn(Optional.of(cart));
        CartService cartService = new CartService(carts, items, products,
                mock(SellerProfileRepository.class), calculator);

        for (int quantity : new int[]{1, 3, 10}) {
            when(items.findByCartIdOrderByIdAsc(10L)).thenReturn(List.of(new CartItem(10L, 5L, quantity)));

            String productPage = (String) catalog.eta(5L, quantity).get("estimatedReadyDate");
            String cartLine = cartService.getCart(1L).items().get(0).estimatedReadyDate().toString();

            assertEquals(productPage, cartLine, "quantity " + quantity);
        }
        // 2400 + 1 x 120 = 2520 minutes, which is two days
        assertEquals(LocalDate.now().plusDays(2).toString(), catalog.eta(5L, 1).get("estimatedReadyDate"));
    }
}