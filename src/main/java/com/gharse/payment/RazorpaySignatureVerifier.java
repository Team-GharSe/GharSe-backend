
package com.gharse.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Verifies the {@code X-Razorpay-Signature} header of a Razorpay webhook call.
 *
 * <p>Razorpay signs the exact bytes of the request body with HMAC-SHA256, using the webhook
 * secret configured in the Razorpay dashboard, and sends the lowercase hex digest in the header.
 * We recompute it over the RAW body bytes (before any JSON parsing, because re-serialised JSON
 * would not be byte-identical) and compare in constant time.
 *
 * <p>Fails closed: if the webhook secret is not configured, nothing is ever considered valid.
 */
@Component
public class RazorpaySignatureVerifier {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final String webhookSecret;

    public RazorpaySignatureVerifier(@Value("${app.razorpay.webhook-secret:}") String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    /**
     * @param rawBody         the request body exactly as received
     * @param signatureHeader value of the X-Razorpay-Signature header (may be null)
     * @return true only if the header matches the HMAC of the body
     */
    public boolean isValid(byte[] rawBody, String signatureHeader) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return false;
        }
        if (rawBody == null || signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        byte[] expected = hexHmac(rawBody).getBytes(StandardCharsets.US_ASCII);
        byte[] provided = signatureHeader.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        // Constant-time comparison: do not use String.equals for secrets/signatures.
        return MessageDigest.isEqual(expected, provided);
    }

    private String hexHmac(byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return HexFormat.of().formatHex(mac.doFinal(data));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is not available", e);
        }
    }
}