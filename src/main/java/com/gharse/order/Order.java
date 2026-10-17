package com.gharse.order;

import jakarta.persistence.*;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity(name = "GharSeOrder")
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "order_id")
    private Long id;

    // Scalar FKs follow the current Product/SellerProfile style. V1 enforces
    // the relationships, including one payment to at most one order.
    @Column(name = "payment_id", nullable = false, unique = true)
    private Long paymentId;
    @Column(name = "buyer_id", nullable = false)
    private Long buyerId;
    @Column(name = "seller_profile_id", nullable = false)
    private Long sellerProfileId;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;
    @Enumerated(EnumType.STRING)
    @Column(name = "fulfilment_mode_selected", nullable = false)
    private FulfilmentMode fulfilmentModeSelected;
    @Column(name = "delivery_address_snapshot", length = 255)
    private String deliveryAddressSnapshot;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    // Same database-owned timestamp pattern as the current Product entity.
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    protected Order() {}

    /** Called by module F's trusted payment/order transaction, not by a public create route. */
    public Order(Long paymentId, Long buyerId, Long sellerProfileId,
                 FulfilmentMode mode, String deliveryAddressSnapshot) {
        this.paymentId = Objects.requireNonNull(paymentId, "paymentId");
        this.buyerId = Objects.requireNonNull(buyerId, "buyerId");
        this.sellerProfileId = Objects.requireNonNull(sellerProfileId, "sellerProfileId");
        this.fulfilmentModeSelected = Objects.requireNonNull(mode, "fulfilment mode");
        this.deliveryAddressSnapshot = deliveryAddressSnapshot;
        this.status = OrderStatus.CONFIRMED;
    }

    // No public status setter: module G owns validated transitions.
    void transitionTo(OrderStatus requested) {
        OrderStatusValidator.validate(status, requested);
        status = requested;
    }

    public Long getId() { return id; }
    public Long getPaymentId() { return paymentId; }
    public Long getBuyerId() { return buyerId; }
    public Long getSellerProfileId() { return sellerProfileId; }
    public OrderStatus getStatus() { return status; }
    public FulfilmentMode getFulfilmentModeSelected() { return fulfilmentModeSelected; }
    public String getDeliveryAddressSnapshot() { return deliveryAddressSnapshot; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
