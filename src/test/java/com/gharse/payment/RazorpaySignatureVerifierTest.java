package com.gharse.payment;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RazorpaySignatureVerifierTest {

    private static final String SECRET = "whsec_test_secret";

    private static final byte[] BODY = ("{\"event\":\"payment.captured\",\"payload\":{\"payment\":{\"entity\":"
            + "{\"order_id\":\"order_TEST123\",\"amount\":50000}}}}").getBytes(StandardCharsets.UTF_8);

    // Independently computed with Python: hmac.new(SECRET, BODY, sha256).hexdigest()
    private static final String VALID_SIGNATURE =
            "2439c355a820beb64192ccc09fecb31553b5b2daedad6e430abf7dd505171684";

    private final RazorpaySignatureVerifier verifier = new RazorpaySignatureVerifier(SECRET);

    @Test
    void acceptsCorrectSignature() {
        assertTrue(verifier.isValid(BODY, VALID_SIGNATURE));
    }

    @Test
    void acceptsUppercaseAndPaddedSignature() {
        assertTrue(verifier.isValid(BODY, "  " + VALID_SIGNATURE.toUpperCase() + " "));
    }

    @Test
    void rejectsTamperedBody() {
        byte[] tampered = new String(BODY, StandardCharsets.UTF_8)
                .replace("50000", "10000").getBytes(StandardCharsets.UTF_8);
        assertFalse(verifier.isValid(tampered, VALID_SIGNATURE));
    }

    @Test
    void rejectsBodyThatIsReformattedEvenIfJsonIsEquivalent() {
        byte[] withSpaces = new String(BODY, StandardCharsets.UTF_8)
                .replace("\"event\":", "\"event\": ").getBytes(StandardCharsets.UTF_8);
        assertFalse(verifier.isValid(withSpaces, VALID_SIGNATURE));
    }

    @Test
    void rejectsSignatureMadeWithAnotherSecret() {
        RazorpaySignatureVerifier other = new RazorpaySignatureVerifier("some_other_secret");
        assertFalse(other.isValid(BODY, VALID_SIGNATURE));
    }

    @Test
    void rejectsMissingOrBlankSignature() {
        assertFalse(verifier.isValid(BODY, null));
        assertFalse(verifier.isValid(BODY, ""));
        assertFalse(verifier.isValid(BODY, "   "));
    }

    @Test
    void rejectsMissingBody() {
        assertFalse(verifier.isValid(null, VALID_SIGNATURE));
    }

    @Test
    void rejectsGarbageAndTruncatedSignature() {
        assertFalse(verifier.isValid(BODY, "not-a-signature"));
        assertFalse(verifier.isValid(BODY, VALID_SIGNATURE.substring(0, 32)));
    }

    @Test
    void failsClosedWhenSecretIsNotConfigured() {
        assertFalse(new RazorpaySignatureVerifier("").isValid(BODY, VALID_SIGNATURE));
        assertFalse(new RazorpaySignatureVerifier("   ").isValid(BODY, VALID_SIGNATURE));
        assertFalse(new RazorpaySignatureVerifier(null).isValid(BODY, VALID_SIGNATURE));
    }
}