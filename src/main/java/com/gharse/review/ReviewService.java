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
import com.gharse.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class ReviewService {

    private static final int MAX_REVIEWS_LISTED = 50;

    private final ReviewRepository reviews;
    private final OrderItemRepository orderItems;
    private final OrderRepository orders;
    private final ProductRepository products;
    private final UserRepository users;

    public ReviewService(ReviewRepository reviews, OrderItemRepository orderItems,
                         OrderRepository orders, ProductRepository products,
                         UserRepository users) {
        this.reviews = reviews;
        this.orderItems = orderItems;
        this.orders = orders;
        this.products = products;
        this.users = users;
    }

    @Transactional
    public ReviewResponse create(AuthenticatedUser user, Long orderItemId, CreateReviewRequest req) {
        // 1. The item must exist.
        OrderItem item = orderItems.findById(orderItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found."));

        // 2. It must belong to this buyer. Same 404 as "missing", so ids can't be probed.
        Order order = orders.findById(item.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found."));
        if (!order.getBuyerId().equals(user.id())) {
            throw new ResourceNotFoundException("Order item not found.");
        }

        // 3. Only after delivery (FR23).
        if (order.getStatus() != OrderStatus.DELIVERED) {
            throw new ConflictException("You can review this item after it has been delivered.");
        }

        // 4. Only once per item.
        if (reviews.existsByOrderItemId(orderItemId)) {
            throw new ConflictException("You have already reviewed this item.");
        }

        String comment = req.comment() == null ? null : req.comment().trim();
        if (comment != null && comment.isEmpty()) {
            comment = null;
        }

        Review saved;
        try {
            // The database UNIQUE key is the real guard against a double-click race.
            saved = reviews.saveAndFlush(new Review(orderItemId, req.rating(), comment));
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("You have already reviewed this item.");
        }

        // 5. Recompute the product's cached rating (never increment, to avoid drift).
        Long productId = item.getProductId();
        Double avg = reviews.averageRatingForProduct(productId);
        long count = reviews.countForProduct(productId);
        BigDecimal rounded = avg == null
                ? BigDecimal.ZERO
                : BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);
        reviews.updateProductRating(productId, rounded, (int) count);

        return ReviewResponse.of(saved, productId, firstName(user.id()));
    }

    /** Public list for the product page: newest first, capped, first names only. */
    @Transactional(readOnly = true)
    public List<ReviewResponse> listForProduct(Long productId) {
        products.findByIdAndActiveTrue(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found."));

        return reviews.findForProductNewestFirst(productId, PageRequest.of(0, MAX_REVIEWS_LISTED))
                .stream()
                .map(r -> ReviewResponse.of(r, productId, reviewerFirstName(r)))
                .toList();
    }

    // At most 50 rows, so a few small lookups per review are fine at prototype scale.
    private String reviewerFirstName(Review r) {
        return orderItems.findById(r.getOrderItemId())
                .flatMap(item -> orders.findById(item.getOrderId()))
                .map(order -> firstName(order.getBuyerId()))
                .orElse("Customer");
    }

    private String firstName(Long userId) {
        return users.findById(userId)
                .map(u -> u.getName() == null ? "" : u.getName().trim().split("\\s+")[0])
                .filter(n -> !n.isEmpty())
                .orElse("Customer");
    }
}