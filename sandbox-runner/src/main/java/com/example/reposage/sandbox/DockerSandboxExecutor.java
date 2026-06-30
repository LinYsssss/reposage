package com.example.reposage.sandbox;

import java.nio.file.Path;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Runs a sandbox job in a constrained Docker container per {@link ContainerPolicy}, then guarantees
 * the container is gone.
 *
 * <p>The container is always cleaned up (kill + force-remove) after a run and on cancellation, even
 * on timeout or error; cleanup is idempotent because {@link ContainerRuntime#execQuietly} swallows
 * "no such container" failures. A job whose command id is not whitelisted is {@code REJECTED} before
 * anything is launched. This replaces the placeholder executor via {@code @ConditionalOnMissingBean}.
 */
@Component
public class DockerSandboxExecutor implements SandboxExecutor {

    private final ContainerPolicy policy;
    private final ContainerRuntime runtime;
    private final Path workspaceBase;

    public DockerSandboxExecutor(ContainerPolicy policy,
                                 ContainerRuntime runtime,
                                 @Value("${sandbox.workspace-base:/sandbox/workspaces}") String workspaceBase) {
        this.policy = policy;
        this.runtime = runtime;
        this.workspaceBase = Path.of(workspaceBase);
    }

    @Override
    public SandboxResult execute(SandboxJob job) {
        String container = containerName(job.jobId());
        Path workspace = workspaceBase.resolve(job.jobId());

        List<String> runArgs;
        try {
            runArgs = policy.dockerRunArgs(job, container, workspace);
        } catch (IllegalArgumentException ex) {
            return new SandboxResult(job.jobId(), SandboxJobStatus.REJECTED, null, "", false, ex.getMessage());
        }

        try {
            ContainerRuntime.RunOutcome outcome = runtime.run(runArgs, job.limits().timeoutMs());
            if (outcome.timedOut()) {
                return new SandboxResult(job.jobId(), SandboxJobStatus.TIMED_OUT, null,
                        outcome.output(), outcome.truncated(), "timed out");
            }
            SandboxJobStatus status = outcome.exitCode() == 0
                    ? SandboxJobStatus.SUCCEEDED : SandboxJobStatus.FAILED;
            return new SandboxResult(job.jobId(), status, outcome.exitCode(),
                    outcome.output(), outcome.truncated(), null);
        } catch (RuntimeException ex) {
            return new SandboxResult(job.jobId(), SandboxJobStatus.FAILED, null, "", false, ex.getMessage());
        } finally {
            cleanup(container);
        }
    }

    /** Cancels an in-flight job by name; idempotent. */
    public void cancel(String jobId) {
        cleanup(containerName(jobId));
    }

    private void cleanup(String container) {
        runtime.execQuietly(policy.killArgs(container));
        runtime.execQuietly(policy.removeArgs(container));
    }

    private static String containerName(String jobId) {
        return "sandbox-" + jobId;
    }
}
