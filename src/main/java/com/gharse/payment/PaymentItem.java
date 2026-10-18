package com.gharse.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Entity
@Table(name = "payment_item")
public class PaymentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_item_id")
    private Long paymentItemId;

    @Column(name = "payment_id", nullable = false, updatable = false)
    private Long paymentId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Column(name = "product_name_snapshot", nullable = false, updatable = false, length = 150)
    private String productNameSnapshot;

    @Column(name = "price_snapshot", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal priceSnapshot;

    @Column(name = "quantity", nullable = false, updatable = false)
    private int quantity;

    protected PaymentItem() {
        // for JPA
    }

    public PaymentItem(Long paymentId, Long productId, String productNameSnapshot,
                       BigDecimal priceSnapshot, int quantity) {
        if (paymentId == null || productId == null || productNameSnapshot == null || priceSnapshot == null) {
            throw new IllegalArgumentException("paymentId, productId, name and price are required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (priceSnapshot.signum() < 0) {
            throw new IllegalArgumentException("price must not be negative");
        }
        this.paymentId = paymentId;
        this.productId = productId;
        this.productNameSnapshot = productNameSnapshot;
        this.priceSnapshot = priceSnapshot.setScale(2, RoundingMode.UNNECESSARY);
        this.quantity = quantity;
    }

    public Long getPaymentItemId() { return paymentItemId; }
    public Long getPaymentId() { return paymentId; }
    public Long getProductId() { return productId; }
    public String getProductNameSnapshot() { return productNameSnapshot; }
    public BigDecimal getPriceSnapshot() { return priceSnapshot; }
    public int getQuantity() { return quantity; }
}