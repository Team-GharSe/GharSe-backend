package com.gharse.review;

import com.gharse.auth.AuthenticatedUser;
import com.gharse.catalog.ProductRepository;
import com.gharse.common.exception.ConflictException;
import com.gharse.common.exception.ResourceNotFoundException;
import com.gharse.order.Order;
import com.gharse.order.OrderItem;
import com.gharse.order.OrderItemRepository;
import com.gharse.order.OrderRepository;
import com.gharse.order.OrderStatus;
import com.gharse.review.dto.CreateReviewRequest;
import com.gharse.review.dto.ReviewResponse;
import com.gharse.user.User;
import com.gharse.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class ReviewServiceTest {
    private final AuthenticatedUser buyer = new AuthenticatedUser(20L, "BUYER");

    private ReviewRepository reviews;
    private OrderItemRepository items;
    private OrderRepository orders;
    private ProductRepository products;
    private UserRepository users;
    private ReviewService service;

    @BeforeEach
    void setUp() {
        reviews = mock(ReviewRepository.class);
        items = mock(OrderItemRepository.class);
        orders = mock(OrderRepository.class);
        products = mock(ProductRepository.class);
        users = mock(UserRepository.class);
        service = new ReviewService(reviews, items, orders, products, users);
    }

    private OrderItem item(long orderId, long productId) {
        OrderItem item = mock(OrderItem.class);
        when(item.getOrderId()).thenReturn(orderId);
        when(item.getProductId()).thenReturn(productId);
        when(items.findById(5L)).thenReturn(Optional.of(item));
        return item;
    }

    private Order order(long buyerId, OrderStatus status) {
        Order order = mock(Order.class);
        when(order.getBuyerId()).thenReturn(buyerId);
        when(order.getStatus()).thenReturn(status);
        when(orders.findById(10L)).thenReturn(Optional.of(order));
        return order;
    }

    private void deliveredOrderOfBuyer() {
        item(10L, 99L);
        order(20L, OrderStatus.DELIVERED);
    }

    private void buyerNamed(String name) {
        User user = mock(User.class);
        when(user.getName()).thenReturn(name);
        when(users.findById(20L)).thenReturn(Optional.of(user));
    }

    @Test
    void missingItemIsNotFound() {
        when(items.findById(5L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> service.create(buyer, 5L, new CreateReviewRequest(5, "Great")));
        verify(reviews, never()).saveAndFlush(any());
    }

    @Test
    void someoneElsesItemIsNotFound() {
        item(10L, 99L);
        order(21L, OrderStatus.DELIVERED);
        var ex = assertThrows(ResourceNotFoundException.class,
                () -> service.create(buyer, 5L, new CreateReviewRequest(5, "Great")));
        assertEquals("Order item not found.", ex.getMessage());
        verify(reviews, never()).saveAndFlush(any());
    }

    @Test
    void notDeliveredIsConflict() {
        item(10L, 99L);
        order(20L, OrderStatus.OUT_FOR_DELIVERY);
        assertThrows(ConflictException.class,
                () -> service.create(buyer, 5L, new CreateReviewRequest(5, "Great")));
        verify(reviews, never()).saveAndFlush(any());
    }

    @Test
    void duplicateReviewIsConflict() {
        deliveredOrderOfBuyer();
        when(reviews.existsByOrderItemId(5L)).thenReturn(true);
        assertThrows(ConflictException.class,
                () -> service.create(buyer, 5L, new CreateReviewRequest(5, "Great")));
        verify(reviews, never()).saveAndFlush(any());
    }

    @Test
    void doubleClickRaceCaughtByUniqueKeyIsConflict() {
        deliveredOrderOfBuyer();
        when(reviews.existsByOrderItemId(5L)).thenReturn(false);
        when(reviews.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("dup"));
        assertThrows(ConflictException.class,
                () -> service.create(buyer, 5L, new CreateReviewRequest(5, "Great")));
        verify(reviews, never()).updateProductRating(anyLong(), any(), anyInt());
    }

    @Test
    void happyPathSavesReviewAndRecomputesProductRating() {
        deliveredOrderOfBuyer();
        buyerNamed("Priya Sharma");
        when(reviews.existsByOrderItemId(5L)).thenReturn(false);
        when(reviews.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reviews.averageRatingForProduct(99L)).thenReturn(4.3333);
        when(reviews.countForProduct(99L)).thenReturn(3L);

        ReviewResponse res = service.create(buyer, 5L, new CreateReviewRequest(4, "  Tasty  "));

        assertEquals(4, res.rating());
        assertEquals("Tasty", res.comment());
        assertEquals("Priya", res.reviewerName());
        assertEquals(99L, res.productId());
        verify(reviews).updateProductRating(99L, new BigDecimal("4.33"), 3);
    }

    @Test
    void blankCommentIsStoredAsNull() {
        deliveredOrderOfBuyer();
        buyerNamed("Priya Sharma");
        when(reviews.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(reviews.averageRatingForProduct(99L)).thenReturn(5.0);
        when(reviews.countForProduct(99L)).thenReturn(1L);

        ReviewResponse res = service.create(buyer, 5L, new CreateReviewRequest(5, "   "));

        assertNull(res.comment());
    }

    @Test
    void listingUnknownProductIsNotFound() {
        when(products.findByIdAndActiveTrue(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.listForProduct(99L));
    }
}