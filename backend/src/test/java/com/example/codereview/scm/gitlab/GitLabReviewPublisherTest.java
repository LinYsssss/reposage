package com.example.codereview.scm.gitlab;

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
 * Verifies the GitLab publisher's MR note and commit status payloads and the patch-approval gate,
 * using a recording HTTP server.
 */
class GitLabReviewPublisherTest {

    private final GitLabReviewPublisher publisher = new GitLabReviewPublisher(RestClient.builder());
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
                List.of("NPE in OrderService.java:42"),
                List.of("https://app/evidence/1"),
                "https://app/runs/99",
                ReviewPublication.PatchValidationState.VALIDATED,
                exposesPatch);
    }

    @Test
    void publishesMergeRequestNoteWithAllReviewFields() {
        publisher.publishNote(server.baseUrl(), "tok", "99", 5, publication(false), false);

        RecordingHttpServer.Recorded request = server.requests().get(0);
        assertThat(request.method()).isEqualTo("POST");
        assertThat(request.path()).isEqualTo("/api/v4/projects/99/merge_requests/5/notes");
        assertThat(request.headers().get("PRIVATE-TOKEN")).isEqualTo("tok");
        assertThat(request.body())
                .contains("Found 2 blocking issues")
                .contains("NPE in OrderService.java:42")
                .contains("https://app/evidence/1")
                .contains("https://app/runs/99")
                .contains("VALIDATED");
    }

    @Test
    void publishesCommitStatusMappingConclusionToFailed() {
        publisher.publishCommitStatus(server.baseUrl(), "tok", "99", "headsha111", publication(false), false);

        RecordingHttpServer.Recorded request = server.requests().get(0);
        assertThat(request.path()).isEqualTo("/api/v4/projects/99/statuses/headsha111");
        assertThat(request.body())
                .contains("failed")          // ACTION_REQUIRED -> failed
                .contains("https://app/runs/99");
    }

    @Test
    void refusesPatchExposingPublicationWithoutApproval() {
        assertThatThrownBy(() -> publisher.publishNote(
                server.baseUrl(), "tok", "99", 5, publication(true), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("approval");
        assertThat(server.requests()).isEmpty();
    }

    @Test
    void allowsPatchExposingPublicationWhenApproved() {
        publisher.publishNote(server.baseUrl(), "tok", "99", 5, publication(true), true);
        assertThat(server.requests()).hasSize(1);
    }
}
