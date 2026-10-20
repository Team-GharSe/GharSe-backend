package com.gharse.review;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "review")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "review_id")
    private Long id;

    // One review per purchased line item (UNIQUE in the database).
    @Column(name = "order_item_id", nullable = false, unique = true, updatable = false)
    private Long orderItemId;

    // The table column is TINYINT, so we say so explicitly. Otherwise Hibernate's
    // startup schema check expects INTEGER and refuses to start.
    @JdbcTypeCode(SqlTypes.TINYINT)
    @Column(name = "rating", nullable = false)
    private Integer rating;

    @Column(name = "comment", columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Review() {}

    public Review(Long orderItemId, Integer rating, String comment) {
        this.orderItemId = Objects.requireNonNull(orderItemId, "orderItemId");
        this.rating = Objects.requireNonNull(rating, "rating");
        this.comment = comment;
    }

    public Long getId() { return id; }
    public Long getOrderItemId() { return orderItemId; }
    public Integer getRating() { return rating; }
    public String getComment() { return comment; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}