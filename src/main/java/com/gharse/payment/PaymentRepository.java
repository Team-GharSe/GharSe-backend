package com.gharse.payment;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** Status endpoint: owner-only lookup. */
    Optional<Payment> findByPaymentIdAndBuyerId(Long paymentId, Long buyerId);

    Optional<Payment> findByRazorpayOrderId(String razorpayOrderId);

    /** Webhook: lock the row so concurrent deliveries of the same event serialise. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.razorpayOrderId = :razorpayOrderId")
    Optional<Payment> findByRazorpayOrderIdForUpdate(@Param("razorpayOrderId") String razorpayOrderId);
}