package com.gharse.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RazorpayOrderResponse(String id, long amount, String currency, String status) {}