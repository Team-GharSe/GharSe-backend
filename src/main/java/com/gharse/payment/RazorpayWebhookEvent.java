package com.gharse.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RazorpayWebhookEvent(String event, Payload payload) {

    /** Null-safe accessor for payload.payment.entity. */
    public PaymentEntity paymentEntity() {
        if (payload == null || payload.payment() == null) return null;
        return payload.payment().entity();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Payload(PaymentWrapper payment) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentWrapper(PaymentEntity entity) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentEntity(
            String id,
            Long amount,
            String currency,
            String status,
            @JsonProperty("order_id") String orderId,
            @JsonProperty("error_code") String errorCode,
            @JsonProperty("error_description") String errorDescription) {}
}