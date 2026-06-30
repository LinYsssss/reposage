package com.example.reposage.sandbox;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Builds the locked-down {@code docker run} invocation for a sandbox job and enforces the
 * non-negotiable execution constraints.
 *
 * <p>Every container is launched with no network, a read-only root filesystem, a non-root user,
 * dropped capabilities, no-new-privileges, hard CPU/memory/PID limits, a stop timeout, and a single
 * small writable tmpfs — the analyzed repository is bind-mounted read-only. Commands are resolved
 * <em>only</em> through a fixed whitelist keyed by id; a raw command string from a message can never
 * become the entrypoint. Argument paths are confined to the job workspace after normalization and
 * symlink resolution.
 */
public class ContainerPolicy {

    /** Container-side mount point for the (read-only) analyzed repository. */
    public static final String WORKSPACE_MOUNT = "/work";
    /** Container-side writable scratch area (tmpfs). */
    public static final String WRITABLE_TMP = "/work/.tmp";
    /** Non-root uid:gid (nobody) the container runs as. */
    public static final String NON_ROOT_USER = "65534:65534";

    /** A command the sandbox is permitted to run, resolved from a whitelisted id. */
    public record ResolvedCommand(String commandId, String binary, List<String> baseArgs) {
    }

    private static final Map<String, ResolvedCommand> WHITELIST = Map.of(
            "git.diff", new ResolvedCommand("git.diff", "git", List.of("--no-pager", "diff")),
            "git.file", new ResolvedCommand("git.file", "git", List.of("--no-pager", "show")),
            "code.search", new ResolvedCommand("code.search", "grep", List.of("-rnI")));

    /** Resolves a command id to its whitelisted binary, rejecting anything not pre-approved. */
    public ResolvedCommand resolveCommand(String commandId) {
        ResolvedCommand command = WHITELIST.get(commandId);
        if (command == null) {
            throw new IllegalArgumentException("command not whitelisted: " + commandId);
        }
        return command;
    }

    /** Full {@code docker run ...} argument list for the job. */
    public List<String> dockerRunArgs(SandboxJob job, String containerName, Path hostWorkspace) {
        ResolvedCommand command = resolveCommand(job.commandId());
        SandboxJob.Limits limits = job.limits();

        List<String> args = new ArrayList<>();
        args.add("docker");
        args.add("run");
        args.add("--rm");
        args.add("--name");
        args.add(containerName);
        args.add("--network");
        args.add("none");
        args.add("--read-only");
        args.add("--user");
        args.add(NON_ROOT_USER);
        args.add("--cap-drop");
        args.add("ALL");
        args.add("--security-opt");
        args.add("no-new-privileges");
        args.add("--pids-limit");
        args.add(String.valueOf(limits.pids()));
        args.add("--memory");
        args.add(limits.memoryMb() + "m");
        args.add("--cpus");
        args.add(formatCpus(limits.cpuMillis()));
        args.add("--stop-timeout");
        args.add(String.valueOf(Math.max(1, limits.timeoutMs() / 1000)));
        args.add("--tmpfs");
        args.add(WRITABLE_TMP + ":rw,noexec,nosuid,size=64m");
        args.add("-v");
        args.add(hostWorkspace.toAbsolutePath().normalize() + ":" + WORKSPACE_MOUNT + ":ro");
        args.add("-w");
        args.add(WORKSPACE_MOUNT);
        args.add(job.imageDigest());
        args.add(command.binary());
        args.addAll(command.baseArgs());
        args.addAll(job.args());
        return args;
    }

    /** Idempotent kill of a running container by name. */
    public List<String> killArgs(String containerName) {
        return List.of("docker", "kill", containerName);
    }

    /** Idempotent force-remove of a container by name (succeeds even if already gone). */
    public List<String> removeArgs(String containerName) {
        return List.of("docker", "rm", "-f", containerName);
    }

    /**
     * Resolves {@code candidate} against the job workspace and confirms it stays inside, after
     * normalization and symlink resolution. Rejects {@code ..} traversal, absolute escapes, and
     * symlinks pointing out of the workspace.
     */
    public Path requireWithinWorkspace(Path workspace, String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new SecurityException("path is required");
        }
        Path base = canonicalize(workspace);
        Path resolved = canonicalize(base.resolve(candidate));
        if (!resolved.startsWith(base)) {
            throw new SecurityException("path escapes workspace: " + candidate);
        }
        return resolved;
    }

    private static Path canonicalize(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        try {
            return absolute.toRealPath();
        } catch (IOException ex) {
            return absolute;
        }
    }

    private static String formatCpus(int cpuMillis) {
        return BigDecimal.valueOf(cpuMillis).movePointLeft(3).stripTrailingZeros().toPlainString();
    }
}
