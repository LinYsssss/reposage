package com.example.reposage.sandbox;

/**
 * Executes a verified {@link SandboxJob} and returns its bounded {@link SandboxResult}.
 *
 * <p>Task 7 ships only the interface plus a placeholder bean so the service scaffolds and tests use
 * a fake; the real Docker-backed implementation (network-isolated, read-only root, resource-limited)
 * arrives with the container execution policy.
 */
public interface SandboxExecutor {

    SandboxResult execute(SandboxJob job);
}
