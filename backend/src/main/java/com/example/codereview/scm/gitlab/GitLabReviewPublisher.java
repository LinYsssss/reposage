package com.example.codereview.scm.gitlab;

import com.example.codereview.scm.ReviewPublication;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Publishes an Agent review back to a GitLab merge request as an MR note and a commit status.
 *
 * <p>The note carries the summary, blocking findings, evidence links, the Agent Run URL, and the
 * patch validation state; the commit status maps the conclusion to a pipeline state with the run URL
 * as its target. Publications that expose generated patch content are refused unless approved.
 */
@Component
public class GitLabReviewPublisher {

    private final RestClient restClient;

    public GitLabReviewPublisher(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    /** Posts the review as a merge request note. */
    public void publishNote(String apiBase, String token, String projectId, int mergeRequestIid,
                            ReviewPublication publication, boolean approved) {
        requireApprovalForPatch(publication, approved);
        restClient.post()
                .uri(trimTrailingSlash(apiBase) + "/api/v4/projects/" + encode(projectId)
                        + "/merge_requests/" + mergeRequestIid + "/notes")
                .header("PRIVATE-TOKEN", token)
                .body(Map.of("body", renderNoteMarkdown(publication)))
                .retrieve()
                .toBodilessEntity();
    }

    /** Sets a commit status reflecting the review conclusion. */
    public void publishCommitStatus(String apiBase, String token, String projectId, String sha,
                                    ReviewPublication publication, boolean approved) {
        requireApprovalForPatch(publication, approved);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("state", commitState(publication.conclusion()));
        payload.put("name", "RepoSage");
        payload.put("description", publication.summary());
        payload.put("target_url", publication.agentRunUrl());
        restClient.post()
                .uri(trimTrailingSlash(apiBase) + "/api/v4/projects/" + encode(projectId) + "/statuses/" + sha)
                .header("PRIVATE-TOKEN", token)
                .body(payload)
                .retrieve()
                .toBodilessEntity();
    }

    private static void requireApprovalForPatch(ReviewPublication publication, boolean approved) {
        if (publication.exposesPatchContent() && !approved) {
            throw new IllegalStateException(
                    "publication exposes generated patch content and requires approval before publishing");
        }
    }

    private static String renderNoteMarkdown(ReviewPublication publication) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 🤖 RepoSage Review — ").append(publication.conclusion()).append("\n\n");
        sb.append("> ").append(publication.summary()).append("\n\n");
        if (!publication.blockingFindings().isEmpty()) {
            sb.append("**Blocking findings:**\n");
            publication.blockingFindings().forEach(f -> sb.append("- ").append(f).append('\n'));
            sb.append('\n');
        }
        if (!publication.evidenceLinks().isEmpty()) {
            sb.append("**Evidence:** ").append(String.join(", ", publication.evidenceLinks())).append("\n\n");
        }
        sb.append("**Patch validation:** ").append(publication.patchValidationState()).append("\n\n");
        sb.append("[View Agent Run](").append(publication.agentRunUrl()).append(")\n");
        return sb.toString();
    }

    private static String commitState(ReviewPublication.Conclusion conclusion) {
        return switch (conclusion) {
            case SUCCESS, NEUTRAL -> "success";
            case ACTION_REQUIRED -> "failed";
        };
    }

    private static String encode(String projectId) {
        return URLEncoder.encode(projectId, StandardCharsets.UTF_8);
    }

    private static String trimTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }
}
