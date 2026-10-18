package com.gharse.payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentItemRepository extends JpaRepository<PaymentItem, Long> {

    List<PaymentItem> findByPaymentIdOrderByPaymentItemId(Long paymentId);
}