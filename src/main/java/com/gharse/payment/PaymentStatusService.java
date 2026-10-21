package com.gharse.payment;

import com.gharse.common.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class PaymentStatusService {

    private final PaymentRepository payments;

    public PaymentStatusService(PaymentRepository payments) {
        this.payments = payments;
    }

    public PaymentStatusResponse getStatus(Long buyerId, Long paymentId) {
        Payment payment = payments.findByPaymentIdAndBuyerId(paymentId, buyerId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found."));
        // Step 5 fills in the order id once the webhook can create orders.
        return new PaymentStatusResponse(payment.getPaymentId(), payment.getStatus(), null);
    }
}