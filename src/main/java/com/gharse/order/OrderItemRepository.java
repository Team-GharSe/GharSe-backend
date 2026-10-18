package com.gharse.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
