package com.gharse.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByBuyerIdOrderByCreatedAtDescIdDesc(Long buyerId);
    List<Order> findBySellerProfileIdOrderByCreatedAtDescIdDesc(Long sellerProfileId);
    List<Order> findBySellerProfileIdAndStatusOrderByCreatedAtDescIdDesc(
        Long sellerProfileId, OrderStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from GharSeOrder o where o.id = :id and o.sellerProfileId = :sellerId")
    Optional<Order> findOwnedForUpdate(@Param("id") Long id, @Param("sellerId") Long sellerId);
    Optional<Order> findByPaymentId(Long paymentId);
}
