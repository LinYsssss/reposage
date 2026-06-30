package com.example.reposage.sandbox;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Read-only repository tools that run inside the workspace: bounded file read and bounded code
 * search. Every path is confined to the workspace (via {@link ContainerPolicy}) and every output is
 * truncated to a ceiling with a {@code truncated} flag, so a huge file or a pathological search can
 * never flood the caller.
 */
public class RepositoryReadTools {

    /** Bounded text output with truncation metadata. */
    public record BoundedText(String content, boolean truncated) {
    }

    private static final int DEFAULT_MAX_BYTES = 64 * 1024;
    private static final int DEFAULT_MAX_RESULTS = 200;
    private static final long SEARCH_FILE_BYTE_LIMIT = 1024 * 1024;

    private final ContainerPolicy containerPolicy;

    public RepositoryReadTools(ContainerPolicy containerPolicy) {
        this.containerPolicy = containerPolicy;
    }

    /** Reads {@code relativePath} within {@code workspace}, bounded to {@code maxBytes}. */
    public BoundedText readFile(Path workspace, String relativePath, int maxBytes) {
        Path file = containerPolicy.requireWithinWorkspace(workspace, relativePath);
        try {
            byte[] all = Files.readAllBytes(file);
            boolean truncated = all.length > maxBytes;
            int length = Math.min(all.length, maxBytes);
            return new BoundedText(new String(all, 0, length, StandardCharsets.UTF_8), truncated);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public BoundedText readFile(Path workspace, String relativePath) {
        return readFile(workspace, relativePath, DEFAULT_MAX_BYTES);
    }

    /** Searches files under {@code workspace} for {@code regex}, bounded to {@code maxResults}. */
    public BoundedText search(Path workspace, String regex, int maxResults) {
        Path base = workspace.toAbsolutePath().normalize();
        Pattern pattern = Pattern.compile(regex);
        List<String> matches = new ArrayList<>();
        boolean[] truncated = {false};

        try (Stream<Path> files = Files.walk(base)) {
            files.filter(Files::isRegularFile).forEach(file -> {
                if (truncated[0] || matches.size() >= maxResults) {
                    truncated[0] = true;
                    return;
                }
                searchFile(base, file, pattern, matches, maxResults, truncated);
            });
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return new BoundedText(String.join("\n", matches), truncated[0]);
    }

    public BoundedText search(Path workspace, String regex) {
        return search(workspace, regex, DEFAULT_MAX_RESULTS);
    }

    private void searchFile(Path base, Path file, Pattern pattern, List<String> matches,
                            int maxResults, boolean[] truncated) {
        try {
            if (Files.size(file) > SEARCH_FILE_BYTE_LIMIT) {
                return; // skip large/binary blobs
            }
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            String relative = base.relativize(file).toString().replace('\\', '/');
            for (int i = 0; i < lines.size(); i++) {
                if (matches.size() >= maxResults) {
                    truncated[0] = true;
                    return;
                }
                if (pattern.matcher(lines.get(i)).find()) {
                    matches.add(relative + ":" + (i + 1) + ":" + lines.get(i).strip());
                }
            }
        } catch (IOException ex) {
            // Unreadable/binary file: skip silently.
        }
    }
}
