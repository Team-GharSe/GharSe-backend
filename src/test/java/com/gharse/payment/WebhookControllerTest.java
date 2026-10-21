package com.gharse.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.json.JsonMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WebhookControllerTest {

    private static final String SECRET = "test-secret";
    private final PaymentWebhookService webhookService = mock(PaymentWebhookService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        WebhookController controller = new WebhookController(
                new RazorpaySignatureVerifier(SECRET), JsonMapper.builder().build(), webhookService);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void missingSignatureIs400AndNothingIsProcessed() throws Exception {
        mvc.perform(post("/v1/payments/webhook").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
        verifyNoInteractions(webhookService);
    }

    @Test
    void wrongSignatureIs400AndNothingIsProcessed() throws Exception {
        mvc.perform(post("/v1/payments/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", "deadbeef").content("{\"event\":\"payment.captured\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(webhookService);
    }

    @Test
    void validCapturedIs200AndDispatchesTheEntity() throws Exception {
        String body = "{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":{\"order_id\":\"order_a\"}}}}";
        mvc.perform(post("/v1/payments/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", sign(body)).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        ArgumentCaptor<RazorpayWebhookEvent.PaymentEntity> captor =
                ArgumentCaptor.forClass(RazorpayWebhookEvent.PaymentEntity.class);
        verify(webhookService).handleCaptured(captor.capture());
        assertEquals("order_a", captor.getValue().orderId());
    }

    @Test
    void validFailedIs200AndDispatchesToFailedHandler() throws Exception {
        String body = "{\"event\":\"payment.failed\",\"payload\":{\"payment\":{\"entity\":{\"order_id\":\"order_b\"}}}}";
        mvc.perform(post("/v1/payments/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", sign(body)).content(body))
                .andExpect(status().isOk());

        ArgumentCaptor<RazorpayWebhookEvent.PaymentEntity> captor =
                ArgumentCaptor.forClass(RazorpayWebhookEvent.PaymentEntity.class);
        verify(webhookService).handleFailed(captor.capture());
        assertEquals("order_b", captor.getValue().orderId());
    }

    @Test
    void unknownEventIs200AndNothingIsProcessed() throws Exception {
        String body = "{\"event\":\"order.paid\"}";
        mvc.perform(post("/v1/payments/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", sign(body)).content(body))
                .andExpect(status().isOk());
        verifyNoInteractions(webhookService);
    }

    @Test
    void signedGarbageIs200NotRetryLoop() throws Exception {
        String body = "not json";
        mvc.perform(post("/v1/payments/webhook").contentType(MediaType.APPLICATION_JSON)
                        .header("X-Razorpay-Signature", sign(body)).content(body))
                .andExpect(status().isOk());
        verifyNoInteractions(webhookService);
    }
}