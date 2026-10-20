package com.gharse.cart;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "cart_item")
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_item_id")
    private Long id;

    @Column(name = "cart_id", nullable = false, updatable = false)
    private Long cartId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    protected CartItem() {}

    public CartItem(Long cartId, Long productId, int quantity) {
        this.cartId = Objects.requireNonNull(cartId, "cartId");
        this.productId = Objects.requireNonNull(productId, "productId");
        changeQuantity(quantity);
    }

    public void changeQuantity(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Cart item quantity must be positive.");
        }
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public Long getCartId() { return cartId; }
    public Long getProductId() { return productId; }
    public Integer getQuantity() { return quantity; }
}