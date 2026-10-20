package com.gharse.payment;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.gharse.order.FulfilmentMode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CheckoutInitiateRequest(
        @JsonProperty("fulfilment_mode") @NotNull FulfilmentMode fulfilmentMode,
        @JsonProperty("delivery_address") @Size(max = 255) String deliveryAddress) {
}