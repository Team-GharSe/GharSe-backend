package com.gharse.payment;

import com.fasterxml.jackson.annotation.JsonProperty;

/** order_id stays null until the webhook has booked the order. */
public record PaymentStatusResponse(
        @JsonProperty("payment_id") Long paymentId,
        @JsonProperty("status") PaymentStatus status,
        @JsonProperty("order_id") Long orderId) {
}