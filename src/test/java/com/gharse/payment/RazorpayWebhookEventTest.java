package com.gharse.payment;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class RazorpayWebhookEventTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void parsesCapturedPayload() {
        String json = """
            {"entity":"event","event":"payment.captured","contains":["payment"],
             "payload":{"payment":{"entity":{"id":"pay_1","amount":50000,"currency":"INR",
               "status":"captured","order_id":"order_abc","notes":{"internal_payment_id":"7"}}}}}
            """;
        RazorpayWebhookEvent e = mapper.readValue(json.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                RazorpayWebhookEvent.class);
        assertEquals("payment.captured", e.event());
        assertEquals("order_abc", e.paymentEntity().orderId());
        assertEquals(50000L, e.paymentEntity().amount());
        assertEquals("INR", e.paymentEntity().currency());
    }

    @Test
    void toleratesEmptyNotesArrayAndMissingPayload() {
        String withArray = """
            {"event":"payment.failed","payload":{"payment":{"entity":{"id":"pay_2","order_id":"order_z",
              "notes":[],"error_code":"BAD_REQUEST_ERROR"}}}}
            """;
        RazorpayWebhookEvent e = mapper.readValue(withArray.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                RazorpayWebhookEvent.class);
        assertEquals("BAD_REQUEST_ERROR", e.paymentEntity().errorCode());

        RazorpayWebhookEvent bare = mapper.readValue("{\"event\":\"order.paid\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                RazorpayWebhookEvent.class);
        assertNull(bare.paymentEntity());
    }
}