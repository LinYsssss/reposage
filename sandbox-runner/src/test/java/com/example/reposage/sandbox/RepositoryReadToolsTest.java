package com.example.reposage.sandbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Bounded, path-confined repository reads. Exercised against a temp workspace and, when present, the
 * real {@code demo-repos/mall-order-service} checkout.
 */
class RepositoryReadToolsTest {

    private final RepositoryReadTools tools = new RepositoryReadTools(new ContainerPolicy());

    @Test
    void readsFileBoundedWithTruncationFlag(@TempDir Path workspace) throws IOException {
        Files.writeString(workspace.resolve("a.txt"), "hello world");

        RepositoryReadTools.BoundedText full = tools.readFile(workspace, "a.txt", 100);
        assertThat(full.content()).isEqualTo("hello world");
        assertThat(full.truncated()).isFalse();

        RepositoryReadTools.BoundedText clipped = tools.readFile(workspace, "a.txt", 5);
        assertThat(clipped.content()).isEqualTo("hello");
        assertThat(clipped.truncated()).isTrue();
    }

    @Test
    void readFileRejectsPathEscape(@TempDir Path workspace) {
        assertThatThrownBy(() -> tools.readFile(workspace, "../secret.txt", 100))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void searchFindsMatchesBoundedByMaxResults(@TempDir Path workspace) throws IOException {
        Files.writeString(workspace.resolve("one.txt"), "a TODO here\nplain line");
        Files.writeString(workspace.resolve("two.txt"), "another TODO there");

        RepositoryReadTools.BoundedText all = tools.search(workspace, "TODO", 10);
        assertThat(all.content().lines().count()).isEqualTo(2);
        assertThat(all.truncated()).isFalse();

        RepositoryReadTools.BoundedText capped = tools.search(workspace, "TODO", 1);
        assertThat(capped.content().lines().count()).isEqualTo(1);
        assertThat(capped.truncated()).isTrue();
    }

    @Test
    void readsAndSearchesTheDemoRepository() {
        Path demo = Path.of("..", "demo-repos", "mall-order-service");
        Assumptions.assumeTrue(Files.isDirectory(demo), "demo repo not present");

        RepositoryReadTools.BoundedText pom = tools.readFile(demo, "pom.xml", 64 * 1024);
        assertThat(pom.content()).contains("<project");

        RepositoryReadTools.BoundedText classes = tools.search(demo, "class ", 50);
        assertThat(classes.content()).contains(".java:");
    }
}
