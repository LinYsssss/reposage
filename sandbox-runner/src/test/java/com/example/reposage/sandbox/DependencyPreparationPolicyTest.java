package com.example.reposage.sandbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Dependency preparation rules: deterministic per-ecosystem cache keys, read-only cache mounts that
 * keep the test container network-isolated, preparation gated behind a single allowlisted command
 * with limits and no secrets, and a missing cache surfacing as ENVIRONMENT_INCOMPLETE (not a finding).
 */
class DependencyPreparationPolicyTest {

    private final DependencyPreparationPolicy policy = new DependencyPreparationPolicy();
    private final ContainerPolicy containerPolicy = new ContainerPolicy();

    @Test
    void detectsEverySupportedEcosystem() {
        Map<String, DependencyPreparationPolicy.Ecosystem> expected = Map.of(
                "pom.xml", DependencyPreparationPolicy.Ecosystem.MAVEN,
                "gradle.lockfile", DependencyPreparationPolicy.Ecosystem.GRADLE,
                "requirements.txt", DependencyPreparationPolicy.Ecosystem.PIP,
                "package-lock.json", DependencyPreparationPolicy.Ecosystem.NPM,
                "pnpm-lock.yaml", DependencyPreparationPolicy.Ecosystem.PNPM,
                "yarn.lock", DependencyPreparationPolicy.Ecosystem.YARN);
        expected.forEach((lockfile, ecosystem) ->
                assertThat(policy.detect(lockfile)).contains(ecosystem));
    }

    @Test
    void cacheKeysAreDeterministicAndContentSensitive() {
        for (String lockfile : List.of("pom.xml", "gradle.lockfile", "requirements.txt",
                "package-lock.json", "pnpm-lock.yaml", "yarn.lock")) {
            byte[] content = ("lock-of-" + lockfile).getBytes(StandardCharsets.UTF_8);
            String key = policy.cacheKey(lockfile, content);

            assertThat(key).isEqualTo(policy.cacheKey(lockfile, content));         // deterministic
            assertThat(policy.detect(lockfile)).hasValueSatisfying(eco ->
                    assertThat(key).startsWith(eco.name().toLowerCase() + "-"));   // namespaced
            assertThat(policy.cacheKey(lockfile, "different".getBytes(StandardCharsets.UTF_8)))
                    .isNotEqualTo(key);                                            // content-sensitive
        }
    }

    @Test
    void untrustedTestJobMountsCacheReadOnlyAndKeepsNetworkNone(@TempDir Path cacheDir) {
        List<String> mount = policy.testJobCacheMountArgs(
                DependencyPreparationPolicy.Ecosystem.MAVEN, cacheDir);
        assertThat(String.join(" ", mount)).contains(":/cache/maven:ro");

        SandboxJob testJob = new SandboxJob("job-t", "ws://archive", "sha256:img", "code.search",
                List.of("TODO"), new SandboxJob.Limits(1000, 256, 64, 30000), 1_900_000_000L, "n");
        assertThat(String.join(" ", containerPolicy.dockerRunArgs(testJob, "c", cacheDir)))
                .contains("--network none");
    }

    @Test
    void preparationIsTheOnlyAllowlistedCommandWithLimitsAndNoSecrets() {
        assertThat(policy.isPreparationCommand(DependencyPreparationPolicy.PREPARE_COMMAND_ID)).isTrue();
        assertThat(policy.isPreparationCommand("git.diff")).isFalse();
        assertThat(policy.isPreparationCommand("code.search")).isFalse();

        DependencyPreparationPolicy.PreparationLimits limits = policy.preparationLimits();
        assertThat(limits.maxBytes()).isPositive();
        assertThat(limits.maxSeconds()).isPositive();

        assertThat(policy.allowsSecret("GITHUB_TOKEN")).isFalse();
        assertThat(policy.allowsSecret("LLM_API_KEY")).isFalse();
    }

    @Test
    void missingCacheIsEnvironmentIncompleteNotAFinding(@TempDir Path cacheRoot) throws IOException {
        DependencyCacheManager manager = new DependencyCacheManager(cacheRoot);
        String key = policy.cacheKey("pom.xml", "x".getBytes(StandardCharsets.UTF_8));

        assertThat(manager.isAvailable(key)).isFalse();
        SandboxResult result = manager.environmentIncomplete("job-1", key);
        assertThat(result.status()).isEqualTo(SandboxJobStatus.ENVIRONMENT_INCOMPLETE);
        assertThat(result.status()).isNotEqualTo(SandboxJobStatus.FAILED);

        manager.prepareDirectory(key);
        assertThat(manager.isAvailable(key)).isTrue();
    }
}
