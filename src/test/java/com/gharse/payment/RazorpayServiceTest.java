package com.gharse.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RazorpayServiceTest {

    private static final String URL = "https://api.razorpay.com/v1/orders";
    private MockRestServiceServer server;
    private RazorpayService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://api.razorpay.com/v1");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new RazorpayService(builder.build(), true);
    }

    @Test
    void sendsPaiseAndParsesResponseIgnoringUnknownFields() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.amount").value(50000))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.receipt").value("pay_7"))
                .andExpect(jsonPath("$.notes.internal_payment_id").value("7"))
                .andRespond(withSuccess(
                        "{\"id\":\"order_abc\",\"amount\":50000,\"currency\":\"INR\",\"status\":\"created\",\"extra\":1}",
                        MediaType.APPLICATION_JSON));

        RazorpayOrderResponse r = service.createOrder(50000, "pay_7", Map.of("internal_payment_id", "7"));

        assertEquals("order_abc", r.id());
        assertEquals(50000, r.amount());
        server.verify();
    }

    @Test
    void httpErrorBecomesRazorpayException() {
        server.expect(requestTo(URL)).andRespond(withBadRequest());
        assertThrows(RazorpayException.class, () -> service.createOrder(100, "pay_1", null));
    }

    @Test
    void amountMismatchIsRejected() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"id\":\"order_x\",\"amount\":999,\"currency\":\"INR\",\"status\":\"created\"}",
                MediaType.APPLICATION_JSON));
        assertThrows(RazorpayException.class, () -> service.createOrder(100, "pay_1", null));
    }

    @Test
    void blankOrderIdIsRejected() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"id\":\"\",\"amount\":100,\"currency\":\"INR\",\"status\":\"created\"}",
                MediaType.APPLICATION_JSON));
        assertThrows(RazorpayException.class, () -> service.createOrder(100, "pay_1", null));
    }

    @Test
    void failsClosedWhenKeysNotConfigured() {
        RazorpayService unconfigured = new RazorpayService(RestClient.builder().build(), false);
        assertThrows(RazorpayException.class, () -> unconfigured.createOrder(100, "pay_1", null));
    }
}