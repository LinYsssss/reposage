package com.example.reposage.sandbox;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Policy for isolated dependency preparation.
 *
 * <p>Two job shapes are kept strictly apart:
 * <ul>
 *   <li><b>Preparation</b> — a separate, allowlisted job ({@link #PREPARE_COMMAND_ID}) that may
 *       fetch dependencies under size/time limits and is given <em>no</em> SCM or LLM secrets.</li>
 *   <li><b>Untrusted test</b> — mounts the prepared cache <em>read-only</em> and keeps
 *       {@code --network none}; it can never reach the network or mutate the cache.</li>
 * </ul>
 * Cache keys are deterministic: the same lockfile content always maps to the same key, so identical
 * dependency sets share a cache and a tampered lockfile cannot collide with a clean one.
 */
public class DependencyPreparationPolicy {

    /** The only command id permitted to perform dependency preparation. */
    public static final String PREPARE_COMMAND_ID = "deps.prepare";

    /** Supported dependency ecosystems and their in-container cache mount points. */
    public enum Ecosystem {
        MAVEN("/cache/maven"),
        GRADLE("/cache/gradle"),
        PIP("/cache/pip"),
        NPM("/cache/npm"),
        PNPM("/cache/pnpm"),
        YARN("/cache/yarn");

        private final String containerCachePath;

        Ecosystem(String containerCachePath) {
            this.containerCachePath = containerCachePath;
        }

        public String containerCachePath() {
            return containerCachePath;
        }
    }

    /** Hard ceilings for a preparation job. */
    public record PreparationLimits(long maxBytes, long maxSeconds) {
    }

    private static final Map<String, Ecosystem> LOCKFILES = Map.of(
            "pom.xml", Ecosystem.MAVEN,
            "gradle.lockfile", Ecosystem.GRADLE,
            "requirements.txt", Ecosystem.PIP,
            "poetry.lock", Ecosystem.PIP,
            "package-lock.json", Ecosystem.NPM,
            "pnpm-lock.yaml", Ecosystem.PNPM,
            "yarn.lock", Ecosystem.YARN);

    /** Resolves an ecosystem from a lockfile name, if recognized. */
    public Optional<Ecosystem> detect(String lockfileName) {
        return Optional.ofNullable(LOCKFILES.get(lockfileName));
    }

    /** Deterministic cache key for an ecosystem + lockfile content. */
    public String cacheKey(Ecosystem ecosystem, byte[] lockfileContent) {
        return ecosystem.name().toLowerCase() + "-" + sha256Hex(lockfileContent);
    }

    /** Deterministic cache key from a lockfile name + content; rejects unknown lockfiles. */
    public String cacheKey(String lockfileName, byte[] lockfileContent) {
        Ecosystem ecosystem = detect(lockfileName)
                .orElseThrow(() -> new IllegalArgumentException("unrecognized lockfile: " + lockfileName));
        return cacheKey(ecosystem, lockfileContent);
    }

    /** Only the preparation command id is allowed to fetch dependencies. */
    public boolean isPreparationCommand(String commandId) {
        return PREPARE_COMMAND_ID.equals(commandId);
    }

    /** Read-only cache mount for an untrusted test job. */
    public List<String> testJobCacheMountArgs(Ecosystem ecosystem, Path hostCacheDir) {
        return List.of("-v",
                hostCacheDir.toAbsolutePath().normalize() + ":" + ecosystem.containerCachePath() + ":ro");
    }

    /** Size/time limits enforced on preparation jobs. */
    public PreparationLimits preparationLimits() {
        return new PreparationLimits(512L * 1024 * 1024, 300);
    }

    /**
     * Whether a given environment variable may be exposed to a preparation job. Always false: prep
     * jobs never receive SCM or LLM secrets.
     */
    public boolean allowsSecret(String environmentVariable) {
        return false;
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
