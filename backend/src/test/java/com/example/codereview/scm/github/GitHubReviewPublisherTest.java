package com.example.codereview.scm.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.codereview.scm.RecordingHttpServer;
import com.example.codereview.scm.ReviewPublication;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Verifies the GitHub publisher's comment and Check Run payloads and the patch-approval gate, using
 * a recording HTTP server.
 */
class GitHubReviewPublisherTest {

    private final GitHubReviewPublisher publisher = new GitHubReviewPublisher(RestClient.builder());
    private RecordingHttpServer server;

    @BeforeEach
    void start() throws IOException {
        server = new RecordingHttpServer();
    }

    @AfterEach
    void stop() {
        server.close();
    }

    private static ReviewPublication publication(boolean exposesPatch) {
        return new ReviewPublication(
                ReviewPublication.Conclusion.ACTION_REQUIRED,
                "Found 2 blocking issues",
                List.of("NPE in OrderService.java:42", "SQLi in AdminOrderController"),
                List.of("https://app/evidence/1"),
                "https://app/runs/99",
                ReviewPublication.PatchValidationState.VALIDATED,
                exposesPatch);
    }

    @Test
    void publishesCommentWithAllReviewFields() {
        publisher.publishComment(server.baseUrl(), "tok", "acme/widgets", 7, publication(false), false);

        assertThat(server.requests()).hasSize(1);
        RecordingHttpServer.Recorded request = server.requests().get(0);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/repos/acme/widgets/issues/7/comments");
        assertThat(request.headers().get("Authorization")).isEqualTo("Bearer tok");
        assertThat(request.body())
                .contains("Found 2 blocking issues")
                .contains("NPE in OrderService.java:42")
                .contains("https://app/evidence/1")
                .contains("https://app/runs/99")
                .contains("VALIDATED");
    }

    @Test
    void publishesCheckRunWithMappedConclusion() {
        publisher.publishCheckRun(server.baseUrl(), "tok", "acme/widgets", "headsha111", publication(false), false);

        RecordingHttpServer.Recorded request = server.requests().get(0);
        assertThat(request.path()).isEqualTo("/repos/acme/widgets/check-runs");
        assertThat(request.body())
                .contains("headsha111")
                .contains("action_required")
                .contains("completed");
    }

    @Test
    void refusesPatchExposingPublicationWithoutApproval() {
        assertThatThrownBy(() -> publisher.publishComment(
                server.baseUrl(), "tok", "acme/widgets", 7, publication(true), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("approval");
        assertThat(server.requests()).isEmpty();
    }

    @Test
    void allowsPatchExposingPublicationWhenApproved() {
        publisher.publishComment(server.baseUrl(), "tok", "acme/widgets", 7, publication(true), true);
        assertThat(server.requests()).hasSize(1);
    }
}
