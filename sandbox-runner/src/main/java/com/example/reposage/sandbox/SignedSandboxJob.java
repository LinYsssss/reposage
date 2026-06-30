package com.example.reposage.sandbox;

/**
 * Wire envelope carried on the queue: a {@link SandboxJob} plus its detached HMAC signature. The
 * runner re-derives the canonical form from {@code job} and checks it against {@code signature},
 * so the transport JSON encoding is irrelevant to verification.
 */
public record SignedSandboxJob(SandboxJob job, String signature) {
    public SignedSandboxJob {
        if (job == null) {
            throw new IllegalArgumentException("job is required");
        }
        if (signature == null || signature.isBlank()) {
            throw new IllegalArgumentException("signature is required");
        }
    }
}
