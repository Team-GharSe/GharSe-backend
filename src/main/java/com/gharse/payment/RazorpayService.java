package com.gharse.payment;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

@Service
public class RazorpayService {

    private final RestClient client;
    private final boolean configured;

    @Autowired
    public RazorpayService(
            @Value("${app.razorpay.key-id:}") String keyId,
            @Value("${app.razorpay.key-secret:}") String keySecret,
            @Value("${app.razorpay.base-url:https://api.razorpay.com/v1}") String baseUrl) {
        this.configured = !keyId.isBlank() && !keySecret.isBlank();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory);
        if (configured) {
            builder.defaultHeaders(h -> h.setBasicAuth(keyId, keySecret));
        }
        this.client = builder.build();
    }

    /** Test-only constructor: inject a pre-built client (e.g. bound to MockRestServiceServer). */
    RazorpayService(RestClient client, boolean configured) {
        this.client = client;
        this.configured = configured;
    }

    public RazorpayOrderResponse createOrder(long amountPaise, String receipt, Map<String, String> notes) {
        if (!configured) {
            throw new RazorpayException("Razorpay keys are not configured");
        }
        if (amountPaise <= 0) {
            throw new IllegalArgumentException("amountPaise must be positive");
        }
        Map<String, Object> body = Map.of(
                "amount", amountPaise,
                "currency", "INR",
                "receipt", receipt,
                "notes", notes == null ? Map.of() : notes);
        RazorpayOrderResponse response;
        try {
            response = client.post()
                    .uri("/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(RazorpayOrderResponse.class);
        } catch (RestClientException e) {
            throw new RazorpayException("Razorpay order creation failed: " + e.getMessage(), e);
        }
        if (response == null || response.id() == null || response.id().isBlank()) {
            throw new RazorpayException("Razorpay returned no order id");
        }
        if (response.amount() != amountPaise) {
            throw new RazorpayException("Razorpay order amount mismatch");
        }
        return response;
    }
}