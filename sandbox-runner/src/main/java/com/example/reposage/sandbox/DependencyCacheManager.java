package com.example.reposage.sandbox;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Manages on-disk dependency caches under a single root, keyed by the deterministic cache key from
 * {@link DependencyPreparationPolicy}.
 *
 * <p>When a test job needs a cache that has not been prepared, the run is reported as
 * {@link SandboxJobStatus#ENVIRONMENT_INCOMPLETE} — explicitly NOT a code finding, so a missing
 * toolchain never masquerades as a defect in the reviewed code.
 */
public class DependencyCacheManager {

    private final Path cacheRoot;

    public DependencyCacheManager(Path cacheRoot) {
        this.cacheRoot = cacheRoot;
    }

    /** The prepared cache directory for a key, if it exists. */
    public Optional<Path> resolve(String cacheKey) {
        Path dir = cacheRoot.resolve(cacheKey);
        return Files.isDirectory(dir) ? Optional.of(dir) : Optional.empty();
    }

    public boolean isAvailable(String cacheKey) {
        return resolve(cacheKey).isPresent();
    }

    /** Creates (idempotently) the cache directory for a preparation job to populate. */
    public Path prepareDirectory(String cacheKey) throws IOException {
        Path dir = cacheRoot.resolve(cacheKey);
        Files.createDirectories(dir);
        return dir;
    }

    /** Canonical result for a test job whose dependency cache is missing — not a finding. */
    public SandboxResult environmentIncomplete(String jobId, String cacheKey) {
        return new SandboxResult(jobId, SandboxJobStatus.ENVIRONMENT_INCOMPLETE, null, "", false,
                "dependency cache unavailable: " + cacheKey);
    }
}
