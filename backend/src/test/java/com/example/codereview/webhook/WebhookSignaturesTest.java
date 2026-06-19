package com.example.codereview.webhook;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class WebhookSignaturesTest {

    private static final byte[] BODY = "{\"action\":\"opened\"}".getBytes(UTF_8);
    private static final String SECRET = "top-secret";

    @Test
    void acceptsValidSignature() {
        String sig = "sha256=" + WebhookSignatures.hmacSha256Hex(BODY, SECRET);
        assertTrue(WebhookSignatures.verifyGithub(BODY, sig, SECRET));
    }

    @Test
    void rejectsTamperedBody() {
        String sig = "sha256=" + WebhookSignatures.hmacSha256Hex(BODY, SECRET);
        assertFalse(WebhookSignatures.verifyGithub("{\"action\":\"closed\"}".getBytes(UTF_8), sig, SECRET));
    }

    @Test
    void rejectsMissingOrMalformedHeader() {
        assertFalse(WebhookSignatures.verifyGithub(BODY, null, SECRET));
        assertFalse(WebhookSignatures.verifyGithub(BODY, "md5=abc", SECRET));
    }

    @Test
    void skipsWhenNoSecretConfigured() {
        assertTrue(WebhookSignatures.verifyGithub(BODY, null, ""));
        assertTrue(WebhookSignatures.verifyGithub(BODY, null, null));
    }
}
