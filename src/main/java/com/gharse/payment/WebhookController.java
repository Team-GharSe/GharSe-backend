package com.gharse.payment;

import com.gharse.common.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@RestController
@RequestMapping("/v1/payments")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final RazorpaySignatureVerifier verifier;
    private final JsonMapper jsonMapper;
    private final PaymentWebhookService webhookService;

    public WebhookController(RazorpaySignatureVerifier verifier,
                             JsonMapper jsonMapper,
                             PaymentWebhookService webhookService) {
        this.verifier = verifier;
        this.jsonMapper = jsonMapper;
        this.webhookService = webhookService;
    }

    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse<Void>> receive(
            @RequestBody(required = false) byte[] body,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {

        if (!verifier.isValid(body, signature)) {
            log.warn("Webhook rejected: missing or invalid signature");
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(HttpStatus.BAD_REQUEST, "Invalid signature"));
        }

        RazorpayWebhookEvent event;
        try {
            event = jsonMapper.readValue(body, RazorpayWebhookEvent.class);
        } catch (JacksonException e) {
            log.warn("Webhook signed but unparseable; acknowledging");
            return ack("unparseable");
        }
        if (event == null || event.event() == null) {
            return ack("ignored");
        }

        switch (event.event()) {
            case "payment.captured" -> webhookService.handleCaptured(event.paymentEntity());
            case "payment.failed" -> webhookService.handleFailed(event.paymentEntity());
            default -> log.info("Webhook event ignored: {}", event.event());
        }
        return ack("received");
    }

    private ResponseEntity<ApiResponse<Void>> ack(String message) {
        return ResponseEntity.ok(ApiResponse.<Void>ok(message, null));
    }
}