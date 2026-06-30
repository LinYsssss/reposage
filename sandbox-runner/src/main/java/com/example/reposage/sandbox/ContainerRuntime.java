package com.example.reposage.sandbox;

import java.util.List;

/**
 * Abstraction over the container engine so the executor's lifecycle (run, timeout, kill/remove) can
 * be unit-tested without a Docker daemon. The production implementation shells out to the Docker CLI
 * via the restricted socket proxy.
 */
public interface ContainerRuntime {

    /** Sentinel exit code meaning the run exceeded its timeout and was force-killed. */
    int TIMED_OUT = -1;

    /** Outcome of a run: exit code (or {@link #TIMED_OUT}) plus bounded combined output. */
    record RunOutcome(int exitCode, String output, boolean truncated) {
        public boolean timedOut() {
            return exitCode == TIMED_OUT;
        }
    }

    /** Runs the command, enforcing the wall-clock timeout; never throws for ordinary failures. */
    RunOutcome run(List<String> args, long timeoutMs);

    /** Best-effort, error-swallowing execution used for idempotent kill/remove cleanup. */
    void execQuietly(List<String> args);
}
