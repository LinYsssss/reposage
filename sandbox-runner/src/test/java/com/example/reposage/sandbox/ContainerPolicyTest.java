package com.example.reposage.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The container policy must emit a fully locked-down invocation, resolve only whitelisted commands,
 * and confine workspace paths.
 */
class ContainerPolicyTest {

    private final ContainerPolicy policy = new ContainerPolicy();

    private static SandboxJob job(String commandId, List<String> args) {
        return new SandboxJob("job-1", "ws://archive", "sha256:img", commandId, args,
                new SandboxJob.Limits(1500, 256, 64, 30000), 1_900_000_000L, "nonce-1");
    }

    @Test
    void runArgsApplyEveryHardeningConstraint(@TempDir Path workspace) {
        List<String> args = policy.dockerRunArgs(job("git.diff", List.of()), "sandbox-job-1", workspace);
        String joined = String.join(" ", args);

        assertThat(joined).contains("--network none");
        assertThat(joined).contains("--read-only");
        assertThat(joined).contains("--user 65534:65534");          // non-root
        assertThat(joined).contains("--cap-drop ALL");
        assertThat(joined).contains("--security-opt no-new-privileges");
        assertThat(joined).contains("--pids-limit 64");             // PID limit
        assertThat(joined).contains("--memory 256m");               // memory limit
        assertThat(joined).contains("--cpus 1.5");                  // CPU limit (1500 millis)
        assertThat(joined).contains("--stop-timeout 30");           // timeout
        assertThat(joined).contains("--tmpfs /work/.tmp:rw");       // temporary writable workspace
        assertThat(joined).contains(":/work:ro");                   // repo mounted read-only
        assertThat(args).containsSubsequence("docker", "run", "sha256:img", "git");
    }

    @Test
    void resolvesOnlyWhitelistedCommandsAndRejectsRawStrings() {
        assertThat(policy.resolveCommand("git.diff").binary()).isEqualTo("git");
        assertThat(policy.resolveCommand("code.search").binary()).isEqualTo("grep");

        // A command string smuggled in a message is not a whitelisted id.
        assertThatThrownBy(() -> policy.resolveCommand("git diff; rm -rf /"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("not whitelisted");
        assertThatThrownBy(() -> policy.dockerRunArgs(job("/bin/sh", List.of("-c", "curl evil")),
                "c", Path.of(".")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void confinesPathsToWorkspace(@TempDir Path workspace) throws IOException {
        Files.createDirectories(workspace.resolve("src"));
        Files.writeString(workspace.resolve("src/Main.java"), "class Main {}");

        assertThat(policy.requireWithinWorkspace(workspace, "src/Main.java"))
                .startsWith(workspace.toRealPath());

        assertThatThrownBy(() -> policy.requireWithinWorkspace(workspace, "../escape"))
                .isInstanceOf(SecurityException.class);
        // Absolute path outside the workspace.
        String outside = workspace.getParent().resolve("secret.txt").toString();
        assertThatThrownBy(() -> policy.requireWithinWorkspace(workspace, outside))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void rejectsSymlinkThatEscapesWorkspace(@TempDir Path workspace) throws IOException {
        Path outsideDir = Files.createDirectories(workspace.getParent().resolve("outside-" + System.nanoTime()));
        Files.writeString(outsideDir.resolve("secret.txt"), "top secret");
        Path link = workspace.resolve("link");
        try {
            Files.createSymbolicLink(link, outsideDir);
        } catch (IOException | UnsupportedOperationException ex) {
            Assumptions.abort("symlink creation not permitted on this platform");
        }
        assertThatThrownBy(() -> policy.requireWithinWorkspace(workspace, "link/secret.txt"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void killAndRemoveArgsAreWellFormed() {
        assertThat(policy.killArgs("c1")).containsExactly("docker", "kill", "c1");
        assertThat(policy.removeArgs("c1")).containsExactly("docker", "rm", "-f", "c1");
        assertThatCode(() -> policy.removeArgs("c1")).doesNotThrowAnyException();
    }
}
