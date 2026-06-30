package com.example.reposage.sandbox;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Consumes signed sandbox jobs from the dedicated queue, enforces the trust checks, and delegates to
 * the {@link SandboxExecutor}.
 *
 * <p>Order of checks: signature (over canonical bytes) and expiry via {@link SandboxJobSigner}, then
 * the nonce replay guard. Any failure yields a {@link SandboxJobStatus#REJECTED} result and the job
 * is not executed. {@link #handle} is package-visible and time-injectable so it can be unit-tested
 * with a fake executor and no broker.
 */
@Component
public class SandboxJobConsumer {

    private static final Logger log = LoggerFactory.getLogger(SandboxJobConsumer.class);

    private final SandboxExecutor executor;
    private final SandboxJobSigner signer;
    private final SandboxReplayGuard replayGuard;
    private final String signingSecret;

    public SandboxJobConsumer(SandboxExecutor executor,
                              SandboxJobSigner signer,
                              SandboxReplayGuard replayGuard,
                              @Value("${sandbox.signing-secret:}") String signingSecret) {
        this.executor = executor;
        this.signer = signer;
        this.replayGuard = replayGuard;
        this.signingSecret = signingSecret;
    }

    @RabbitListener(queues = SandboxRunnerRabbitConfig.SANDBOX_JOB_QUEUE)
    public void onMessage(SignedSandboxJob envelope) {
        SandboxResult result = handle(envelope, Instant.now().getEpochSecond());
        log.info("Sandbox job {} -> {}", result.jobId(), result.status());
    }

    SandboxResult handle(SignedSandboxJob envelope, long nowEpochSeconds) {
        SandboxJob job = envelope.job();
        SandboxJobSigner.Verification verification =
                signer.verify(job, envelope.signature(), signingSecret, nowEpochSeconds);
        if (verification != SandboxJobSigner.Verification.VALID) {
            return rejected(job, verification.name());
        }
        if (!replayGuard.checkAndRecord(job.nonce())) {
            return rejected(job, "REPLAY");
        }
        return executor.execute(job);
    }

    private static SandboxResult rejected(SandboxJob job, String reason) {
        return new SandboxResult(job.jobId(), SandboxJobStatus.REJECTED, null, "", false,
                "rejected: " + reason);
    }
}
