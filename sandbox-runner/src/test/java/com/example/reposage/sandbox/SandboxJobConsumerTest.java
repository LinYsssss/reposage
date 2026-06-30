package com.example.reposage.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Consumer trust checks against a fake executor: only a valid, unexpired, non-replayed, correctly
 * signed job reaches the executor; everything else is REJECTED and the executor is never called.
 */
class SandboxJobConsumerTest {

    private static final String KEY = "runner-key";

    private final SandboxJobSigner signer = new SandboxJobSigner();

    private static SandboxJob job(String nonce) {
        return new SandboxJob(
                "job-" + nonce, "ws://archive", "sha256:img", "git.diff",
                List.of("--base", "main"), new SandboxJob.Limits(1000, 256, 64, 30000),
                1_900_000_000L, nonce);
    }

    @Test
    void validJobReachesExecutor() {
        AtomicInteger calls = new AtomicInteger();
        SandboxJobConsumer consumer = consumer(calls);
        SandboxJob job = job("n1");
        SignedSandboxJob envelope = new SignedSandboxJob(job, signer.sign(job, KEY));

        SandboxResult result = consumer.handle(envelope, job.expiryEpochSeconds() - 1);

        assertThat(result.status()).isEqualTo(SandboxJobStatus.SUCCEEDED);
        assertThat(calls).hasValue(1);
    }

    @Test
    void tamperedSignatureIsRejectedWithoutExecuting() {
        AtomicInteger calls = new AtomicInteger();
        SandboxJobConsumer consumer = consumer(calls);
        SandboxJob job = job("n2");
        SignedSandboxJob envelope = new SignedSandboxJob(job, signer.sign(job, KEY) + "ff");

        SandboxResult result = consumer.handle(envelope, job.expiryEpochSeconds() - 1);

        assertThat(result.status()).isEqualTo(SandboxJobStatus.REJECTED);
        assertThat(result.message()).contains("INVALID_SIGNATURE");
        assertThat(calls).hasValue(0);
    }

    @Test
    void expiredJobIsRejectedWithoutExecuting() {
        AtomicInteger calls = new AtomicInteger();
        SandboxJobConsumer consumer = consumer(calls);
        SandboxJob job = job("n3");
        SignedSandboxJob envelope = new SignedSandboxJob(job, signer.sign(job, KEY));

        SandboxResult result = consumer.handle(envelope, job.expiryEpochSeconds() + 1);

        assertThat(result.status()).isEqualTo(SandboxJobStatus.REJECTED);
        assertThat(result.message()).contains("EXPIRED");
        assertThat(calls).hasValue(0);
    }

    @Test
    void replayedNonceIsRejectedAfterFirstUse() {
        AtomicInteger calls = new AtomicInteger();
        SandboxJobConsumer consumer = consumer(calls);
        SandboxJob job = job("n4");
        SignedSandboxJob envelope = new SignedSandboxJob(job, signer.sign(job, KEY));

        assertThat(consumer.handle(envelope, job.expiryEpochSeconds() - 1).status())
                .isEqualTo(SandboxJobStatus.SUCCEEDED);
        SandboxResult replay = consumer.handle(envelope, job.expiryEpochSeconds() - 1);

        assertThat(replay.status()).isEqualTo(SandboxJobStatus.REJECTED);
        assertThat(replay.message()).contains("REPLAY");
        assertThat(calls).hasValue(1);
    }

    private SandboxJobConsumer consumer(AtomicInteger calls) {
        SandboxExecutor fake = job -> {
            calls.incrementAndGet();
            return new SandboxResult(job.jobId(), SandboxJobStatus.SUCCEEDED, 0, "ok", false, "done");
        };
        return new SandboxJobConsumer(fake, signer, new SandboxReplayGuard(), KEY);
    }
}
