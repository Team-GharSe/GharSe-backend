package com.gharse.catalog;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product")
@Getter
@Setter
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long id;

    @Column(name = "seller_profile_id", nullable = false)
    private Long sellerProfileId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfilment_type", nullable = false, length = 20)
    private FulfilmentType fulfilmentType;

    /** Ready-stock only. */
    @Column(name = "inventory_qty")
    private Integer inventoryQty;

    /** JPA optimistic lock (FR14). Hibernate manages it; never set it by hand. */
    @Version
    @Column(nullable = false)
    private Integer version;

    /** Made-to-order only. Feeds the live ETA (FR12). */
    @Column(name = "production_time_minutes_per_unit")
    private Integer productionTimeMinutesPerUnit;

    @Column(name = "supports_self_delivery", nullable = false)
    private boolean supportsSelfDelivery;

    @Column(name = "supports_pickup", nullable = false)
    private boolean supportsPickup;

    @Column(name = "supports_postal", nullable = false)
    private boolean supportsPostal;

    @Column(name = "delivery_radius_km", precision = 5, scale = 2)
    private BigDecimal deliveryRadiusKm;

    /** Set only by admin approval of a VerificationRequest (module D). */
    @Column(name = "is_verified", nullable = false)
    private boolean verified;

    /** Denormalised cache, recomputed when a review is added (module H). */
    @Column(name = "average_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal averageRating = BigDecimal.ZERO;

    /** Denormalised cache, recomputed when a review is added (module H). */
    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    /** Soft delete (FR20). false = hidden from buyers and from search. */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Written by the database on insert and update. */
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
