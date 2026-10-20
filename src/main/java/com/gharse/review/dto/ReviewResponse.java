package com.gharse.review.dto;

import com.gharse.review.Review;

import java.time.LocalDateTime;

public record ReviewResponse(
        Long id,
        Long orderItemId,
        Long productId,
        Integer rating,
        String comment,
        String reviewerName,
        LocalDateTime createdAt) {

    public static ReviewResponse of(Review r, Long productId, String reviewerName) {
        return new ReviewResponse(r.getId(), r.getOrderItemId(), productId,
                r.getRating(), r.getComment(), reviewerName, r.getCreatedAt());
    }
}