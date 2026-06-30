package com.example.reposage.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Safe extraction: traversal/absolute entries and zip bombs are rejected, valid archives unpack, and
 * an escaping symlink in a populated tree is caught.
 */
class SafeArchiveExtractorTest {

    private final SafeArchiveExtractor extractor = new SafeArchiveExtractor();

    @Test
    void resolveSafelyRejectsTraversalAndAbsolutePaths(@TempDir Path base) {
        assertThat(extractor.resolveSafely(base, "src/Main.java").startsWith(base.toAbsolutePath().normalize()))
                .isTrue();
        assertThatThrownBy(() -> extractor.resolveSafely(base, "../evil")).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> extractor.resolveSafely(base, "/etc/passwd")).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> extractor.resolveSafely(base, "C:\\Windows")).isInstanceOf(SecurityException.class);
    }

    @Test
    void extractsValidArchive(@TempDir Path target) throws IOException {
        byte[] zip = zip(Map.of("a.txt", "alpha", "dir/b.txt", "beta"));
        int entries = extractor.extractZip(new ByteArrayInputStream(zip), target, SafeArchiveExtractor.DEFAULT_LIMITS);

        assertThat(entries).isEqualTo(2);
        assertThat(Files.readString(target.resolve("a.txt"))).isEqualTo("alpha");
        assertThat(Files.readString(target.resolve("dir/b.txt"))).isEqualTo("beta");
    }

    @Test
    void rejectsTraversalEntryDuringExtraction(@TempDir Path target) {
        byte[] zip = zip(new LinkedHashMap<>(Map.of("../escape.txt", "pwned")));
        assertThatThrownBy(() -> extractor.extractZip(
                new ByteArrayInputStream(zip), target, SafeArchiveExtractor.DEFAULT_LIMITS))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void rejectsOversizedArchive(@TempDir Path target) {
        byte[] zip = zip(Map.of("big.txt", "0123456789"));
        var tiny = new SafeArchiveExtractor.ExtractionLimits(4, 1000);
        assertThatThrownBy(() -> extractor.extractZip(new ByteArrayInputStream(zip), target, tiny))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void rejectsTooManyEntries(@TempDir Path target) {
        byte[] zip = zip(Map.of("a.txt", "a", "b.txt", "b"));
        var oneEntry = new SafeArchiveExtractor.ExtractionLimits(1_000_000, 1);
        assertThatThrownBy(() -> extractor.extractZip(new ByteArrayInputStream(zip), target, oneEntry))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void assertNoEscapingSymlinksCatchesEscape(@TempDir Path workspace) throws IOException {
        Files.writeString(workspace.resolve("ok.txt"), "fine");
        assertThatCode(() -> extractor.assertNoEscapingSymlinks(workspace)).doesNotThrowAnyException();

        Path outside = Files.createDirectories(workspace.getParent().resolve("out-" + System.nanoTime()));
        try {
            Files.createSymbolicLink(workspace.resolve("link"), outside);
        } catch (IOException | UnsupportedOperationException ex) {
            Assumptions.abort("symlink creation not permitted on this platform");
        }
        assertThatThrownBy(() -> extractor.assertNoEscapingSymlinks(workspace))
                .isInstanceOf(SecurityException.class);
    }

    private static byte[] zip(Map<String, String> entries) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            for (Map.Entry<String, String> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return bytes.toByteArray();
    }
}
