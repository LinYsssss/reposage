package com.example.reposage.sandbox;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Core runner beans: the stateless signer, the per-process replay guard, and a placeholder executor.
 *
 * <p>The placeholder backs off ({@link ConditionalOnMissingBean}) once the real Docker executor is
 * registered. Until then it returns {@link SandboxJobStatus#ENVIRONMENT_INCOMPLETE} rather than
 * pretending to run anything — so a misconfigured runner never fabricates a code finding.
 */
@Configuration
public class SandboxRunnerConfig {

    @Bean
    public SandboxJobSigner sandboxJobSigner() {
        return new SandboxJobSigner();
    }

    @Bean
    public ContainerPolicy containerPolicy() {
        return new ContainerPolicy();
    }

    @Bean
    public SandboxReplayGuard sandboxReplayGuard() {
        return new SandboxReplayGuard();
    }

    @Bean
    public DependencyPreparationPolicy dependencyPreparationPolicy() {
        return new DependencyPreparationPolicy();
    }

    @Bean
    public DependencyCacheManager dependencyCacheManager(
            @Value("${sandbox.cache-root:/cache}") String cacheRoot) {
        return new DependencyCacheManager(Path.of(cacheRoot));
    }

    @Bean
    @ConditionalOnMissingBean(SandboxExecutor.class)
    public SandboxExecutor placeholderSandboxExecutor() {
        return job -> new SandboxResult(
                job.jobId(), SandboxJobStatus.ENVIRONMENT_INCOMPLETE, null, "", false,
                "sandbox executor not configured");
    }
}
