package com.gharse.cart;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "cart")
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_id")
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false, unique = true)
    private Long userId;

    /** Single-seller lock (FR11). Null while the cart is empty. */
    @Column(name = "seller_profile_id")
    private Long sellerProfileId;

    protected Cart() {}

    public Cart(Long userId) {
        this.userId = Objects.requireNonNull(userId, "userId");
    }

    public void lockTo(Long sellerProfileId) {
        this.sellerProfileId = Objects.requireNonNull(sellerProfileId, "sellerProfileId");
    }

    public void releaseLock() {
        this.sellerProfileId = null;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getSellerProfileId() { return sellerProfileId; }
}