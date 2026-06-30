package com.example.reposage.sandbox;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extracts an untrusted repository archive into a workspace, refusing anything that could write
 * outside it.
 *
 * <p>Each entry name is rejected if absolute or containing {@code ..}, and the resolved path must
 * stay within the target after normalization. Totals are bounded by {@link ExtractionLimits} (max
 * bytes and max entry count) to stop zip bombs. The extractor never materializes symbolic links;
 * {@link #assertNoEscapingSymlinks(Path)} additionally verifies an already-populated tree has no
 * symlink escaping the workspace.
 */
public class SafeArchiveExtractor {

    /** Ceilings that bound an extraction. */
    public record ExtractionLimits(long maxTotalBytes, int maxEntries) {
    }

    public static final ExtractionLimits DEFAULT_LIMITS =
            new ExtractionLimits(256L * 1024 * 1024, 20_000);

    private static final int BUFFER = 8192;

    /** Extracts a zip stream into {@code targetDir}; returns the number of files written. */
    public int extractZip(InputStream zipStream, Path targetDir, ExtractionLimits limits) throws IOException {
        Path base = targetDir.toAbsolutePath().normalize();
        Files.createDirectories(base);
        long totalBytes = 0;
        int entries = 0;
        try (ZipInputStream zip = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > limits.maxEntries()) {
                    throw new SecurityException("archive has too many entries (> " + limits.maxEntries() + ")");
                }
                Path resolved = resolveSafely(base, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(resolved);
                    continue;
                }
                Files.createDirectories(resolved.getParent());
                totalBytes += copyBounded(zip, resolved, limits.maxTotalBytes() - totalBytes);
            }
        }
        return entries;
    }

    /**
     * Resolves {@code entryName} under {@code base}, rejecting absolute paths and {@code ..}
     * traversal. Package-visible for direct testing.
     */
    Path resolveSafely(Path base, String entryName) {
        if (entryName == null || entryName.isBlank()) {
            throw new SecurityException("empty archive entry name");
        }
        String normalized = entryName.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
            throw new SecurityException("absolute path in archive: " + entryName);
        }
        Path resolved = base.resolve(normalized).normalize();
        if (!resolved.startsWith(base)) {
            throw new SecurityException("archive entry escapes workspace: " + entryName);
        }
        return resolved;
    }

    /** Verifies no symbolic link under {@code root} resolves outside it. */
    public void assertNoEscapingSymlinks(Path root) throws IOException {
        Path base = root.toRealPath();
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isSymbolicLink).forEach(link -> {
                try {
                    if (!link.toRealPath().startsWith(base)) {
                        throw new SecurityException("symlink escapes workspace: " + link);
                    }
                } catch (IOException ex) {
                    throw new SecurityException("unresolvable symlink: " + link);
                }
            });
        }
    }

    private long copyBounded(ZipInputStream zip, Path target, long remaining) throws IOException {
        long written = 0;
        byte[] buffer = new byte[BUFFER];
        try (var out = Files.newOutputStream(target)) {
            int read;
            while ((read = zip.read(buffer)) != -1) {
                written += read;
                if (written > remaining) {
                    throw new SecurityException("archive exceeds size limit");
                }
                out.write(buffer, 0, read);
            }
        }
        return written;
    }
}
