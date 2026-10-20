package com.gharse.payment;

import com.fasterxml.jackson.annotation.JsonProperty;

/** amount is in paise, which is what Razorpay Checkout JS expects. */
public record CheckoutInitiateResponse(
        @JsonProperty("payment_id") Long paymentId,
        @JsonProperty("razorpay_order_id") String razorpayOrderId,
        @JsonProperty("razorpay_key_id") String razorpayKeyId,
        @JsonProperty("amount") long amount,
        @JsonProperty("currency") String currency) {
}