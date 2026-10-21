package com.gharse.payment;

import com.gharse.common.exception.ResourceNotFoundException;
import com.gharse.order.Order;
import com.gharse.order.OrderRepository;
import org.springframework.stereotype.Service;

@Service
public class PaymentStatusService {

    private final PaymentRepository payments;
    private final OrderRepository orders;

    public PaymentStatusService(PaymentRepository payments, OrderRepository orders) {
        this.payments = payments;
        this.orders = orders;
    }

    public PaymentStatusResponse getStatus(Long buyerId, Long paymentId) {
        Payment payment = payments.findByPaymentIdAndBuyerId(paymentId, buyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found."));
        Long orderId = null;
        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            orderId = orders.findByPaymentId(payment.getPaymentId()).map(Order::getId).orElse(null);
        }
        return new PaymentStatusResponse(payment.getPaymentId(), payment.getStatus(), orderId);
    }
}