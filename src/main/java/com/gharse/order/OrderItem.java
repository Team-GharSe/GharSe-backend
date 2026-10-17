package com.gharse.order;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "order_item")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_item_id")
    private Long id;
    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;
    // Reference only. No cascading Product writes/deletes and no live-price lookup.
    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;
    @Column(name = "product_name_snapshot", nullable = false, updatable = false, length = 150)
    private String productNameSnapshot;
    @Column(name = "price_snapshot", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal priceSnapshot;
    @Column(name = "quantity", nullable = false, updatable = false)
    private Integer quantity;

    protected OrderItem() {}

    public OrderItem(Long orderId, Long productId, String nameSnapshot,
                     BigDecimal priceSnapshot, Integer quantity) {
        this.orderId = Objects.requireNonNull(orderId, "orderId");
        this.productId = Objects.requireNonNull(productId, "productId");
        this.productNameSnapshot = Objects.requireNonNull(nameSnapshot, "product name snapshot");
        this.priceSnapshot = Objects.requireNonNull(priceSnapshot, "price snapshot");
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("Order item quantity must be positive.");
        }
        this.quantity = quantity;
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public Long getProductId() { return productId; }
    public String getProductNameSnapshot() { return productNameSnapshot; }
    public BigDecimal getPriceSnapshot() { return priceSnapshot; }
    public Integer getQuantity() { return quantity; }
}
