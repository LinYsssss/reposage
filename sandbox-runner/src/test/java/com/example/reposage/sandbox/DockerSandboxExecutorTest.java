package com.example.reposage.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Executor lifecycle against a fake {@link ContainerRuntime}: success/timeout mapping, kill+remove
 * cleanup after every run and on cancellation (idempotent), and rejection of non-whitelisted
 * commands before anything launches.
 */
class DockerSandboxExecutorTest {

    private static SandboxJob job(String commandId) {
        return new SandboxJob("job-9", "ws://archive", "sha256:img", commandId, List.of(),
                new SandboxJob.Limits(1000, 256, 64, 30000), 1_900_000_000L, "nonce-9");
    }

    @Test
    void successfulRunIsSucceededAndCleansUp() {
        FakeRuntime runtime = new FakeRuntime(new ContainerRuntime.RunOutcome(0, "ok", false));
        DockerSandboxExecutor executor = new DockerSandboxExecutor(new ContainerPolicy(), runtime, "/tmp/ws");

        SandboxResult result = executor.execute(job("git.diff"));

        assertThat(result.status()).isEqualTo(SandboxJobStatus.SUCCEEDED);
        assertThat(result.exitCode()).isZero();
        assertThat(runtime.quietCalls).containsExactly(
                List.of("docker", "kill", "sandbox-job-9"),
                List.of("docker", "rm", "-f", "sandbox-job-9"));
    }

    @Test
    void timeoutKillsAndRemovesContainer() {
        FakeRuntime runtime = new FakeRuntime(
                new ContainerRuntime.RunOutcome(ContainerRuntime.TIMED_OUT, "partial", true));
        DockerSandboxExecutor executor = new DockerSandboxExecutor(new ContainerPolicy(), runtime, "/tmp/ws");

        SandboxResult result = executor.execute(job("git.diff"));

        assertThat(result.status()).isEqualTo(SandboxJobStatus.TIMED_OUT);
        assertThat(runtime.quietCalls).contains(
                List.of("docker", "kill", "sandbox-job-9"),
                List.of("docker", "rm", "-f", "sandbox-job-9"));
    }

    @Test
    void nonWhitelistedCommandRejectedWithoutRunning() {
        FakeRuntime runtime = new FakeRuntime(new ContainerRuntime.RunOutcome(0, "", false));
        DockerSandboxExecutor executor = new DockerSandboxExecutor(new ContainerPolicy(), runtime, "/tmp/ws");

        SandboxResult result = executor.execute(job("evil.command"));

        assertThat(result.status()).isEqualTo(SandboxJobStatus.REJECTED);
        assertThat(runtime.runCalls).isZero();
    }

    @Test
    void cancelIsIdempotent() {
        FakeRuntime runtime = new FakeRuntime(new ContainerRuntime.RunOutcome(0, "", false));
        DockerSandboxExecutor executor = new DockerSandboxExecutor(new ContainerPolicy(), runtime, "/tmp/ws");

        executor.cancel("job-9");
        executor.cancel("job-9");

        // Two cancels => two kill+remove pairs, no error.
        assertThat(runtime.quietCalls).hasSize(4);
    }

    /** Records interactions; never throws (mirrors the execQuietly swallow contract). */
    private static final class FakeRuntime implements ContainerRuntime {
        private final RunOutcome outcome;
        private int runCalls;
        private final List<List<String>> quietCalls = new ArrayList<>();

        private FakeRuntime(RunOutcome outcome) {
            this.outcome = outcome;
        }

        @Override
        public RunOutcome run(List<String> args, long timeoutMs) {
            runCalls++;
            return outcome;
        }

        @Override
        public void execQuietly(List<String> args) {
            quietCalls.add(List.copyOf(args));
        }
    }
}
