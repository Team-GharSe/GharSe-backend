package com.gharse.payment;

import com.gharse.order.FulfilmentMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "buyer_id", nullable = false, updatable = false)
    private Long buyerId;

    @Column(name = "seller_profile_id", nullable = false, updatable = false)
    private Long sellerProfileId;

    /** Razorpay's own order id (plain string, never a FK). Null until Razorpay has created the order. */
    @Column(name = "razorpay_order_id", length = 64)
    private String razorpayOrderId;

    @Column(name = "amount_total", nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal amountTotal;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfilment_mode_selected", nullable = false, updatable = false)
    private FulfilmentMode fulfilmentModeSelected;

    @Column(name = "delivery_address_snapshot", length = 255, updatable = false)
    private String deliveryAddressSnapshot;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    protected Payment() {
        // for JPA
    }

    public Payment(Long buyerId, Long sellerProfileId, BigDecimal amountTotal,
                   FulfilmentMode fulfilmentModeSelected, String deliveryAddressSnapshot) {
        if (buyerId == null || sellerProfileId == null || amountTotal == null || fulfilmentModeSelected == null) {
            throw new IllegalArgumentException("buyerId, sellerProfileId, amountTotal and fulfilmentMode are required");
        }
        if (amountTotal.signum() <= 0) {
            throw new IllegalArgumentException("amountTotal must be positive");
        }
        this.buyerId = buyerId;
        this.sellerProfileId = sellerProfileId;
        // throws ArithmeticException if the amount has more than 2 decimal places
        this.amountTotal = amountTotal.setScale(2, RoundingMode.UNNECESSARY);
        this.fulfilmentModeSelected = fulfilmentModeSelected;
        this.deliveryAddressSnapshot = deliveryAddressSnapshot;
        this.status = PaymentStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    /** Stores Razorpay's order id once, right after the Razorpay call succeeds. */
    public void attachRazorpayOrderId(String razorpayOrderId) {
        if (razorpayOrderId == null || razorpayOrderId.isBlank()) {
            throw new IllegalArgumentException("razorpayOrderId is required");
        }
        if (status != PaymentStatus.PENDING || this.razorpayOrderId != null) {
            throw new IllegalStateException("Razorpay order id can only be attached once, to a PENDING payment");
        }
        this.razorpayOrderId = razorpayOrderId;
    }

    /**
     * Allowed from PENDING, and from FAILED: Razorpay can send payment.failed for one attempt and then
     * payment.captured when the buyer retries on the same Razorpay order.
     */
    public void markSuccess(LocalDateTime confirmedAt) {
        if (status != PaymentStatus.PENDING && status != PaymentStatus.FAILED) {
            throw new IllegalStateException("Cannot mark SUCCESS from " + status);
        }
        this.status = PaymentStatus.SUCCESS;
        this.confirmedAt = confirmedAt;
    }

    public void markFailed() {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Cannot mark FAILED from " + status);
        }
        this.status = PaymentStatus.FAILED;
    }

    /** Used for the double-sale case (money captured, no stock) and for manual refunds. */
    public void markRefunded() {
        if (status == PaymentStatus.REFUNDED) {
            throw new IllegalStateException("Payment is already REFUNDED");
        }
        this.status = PaymentStatus.REFUNDED;
    }

    public Long getPaymentId() { return paymentId; }
    public Long getBuyerId() { return buyerId; }
    public Long getSellerProfileId() { return sellerProfileId; }
    public String getRazorpayOrderId() { return razorpayOrderId; }
    public BigDecimal getAmountTotal() { return amountTotal; }
    public PaymentStatus getStatus() { return status; }
    public FulfilmentMode getFulfilmentModeSelected() { return fulfilmentModeSelected; }
    public String getDeliveryAddressSnapshot() { return deliveryAddressSnapshot; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
}