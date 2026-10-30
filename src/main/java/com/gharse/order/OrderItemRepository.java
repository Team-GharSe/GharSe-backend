package com.gharse.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    List<OrderItem> findByOrderIdOrderByIdAsc(Long orderId);

    /**
     * Units already committed to production for a product (FR12 backlog).
     * Counts only orders whose status is in the given list (CONFIRMED, IN_PRODUCTION).
     * This is a live sum, never a stored counter.
     * Note: the Order entity is registered in JPQL as "GharSeOrder".
     */
    @Query("""
            select coalesce(sum(i.quantity), 0)
            from OrderItem i, GharSeOrder ord
            where i.orderId = ord.id
              and i.productId = :productId
              and ord.status in :openStatuses
            """)
    long sumBacklogUnits(@Param("productId") Long productId,
                         @Param("openStatuses") List<OrderStatus> openStatuses);

    /**
     * Totals for one seller over [from, to). One row: [revenue (BigDecimal), units (Long), orders (Long)].
     * Revenue = sum of price snapshot x quantity. Uses snapshots, so later price edits don't change history.
     */
    @Query("""
            select coalesce(sum(i.priceSnapshot * i.quantity), 0),
                   coalesce(sum(i.quantity), 0),
                   count(distinct ord.id)
            from OrderItem i, GharSeOrder ord
            where i.orderId = ord.id
              and ord.sellerProfileId = :sellerId
              and ord.createdAt >= :from
              and ord.createdAt < :to
            """)
    List<Object[]> analyticsSummary(@Param("sellerId") Long sellerId,
                                    @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to);

    /**
     * Per-day totals for one seller over [from, to). Each row: [date, orders (Long), revenue (BigDecimal)].
     * Days with no orders are not returned; the service fills them in if needed.
     */
    @Query("""
            select cast(ord.createdAt as date),
                   count(distinct ord.id),
                   coalesce(sum(i.priceSnapshot * i.quantity), 0)
            from OrderItem i, GharSeOrder ord
            where i.orderId = ord.id
              and ord.sellerProfileId = :sellerId
              and ord.createdAt >= :from
              and ord.createdAt < :to
            group by cast(ord.createdAt as date)
            order by cast(ord.createdAt as date)
            """)
    List<Object[]> analyticsDaily(@Param("sellerId") Long sellerId,
                                  @Param("from") LocalDateTime from,
                                  @Param("to") LocalDateTime to);
}