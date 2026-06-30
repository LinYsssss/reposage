package com.example.codereview.scm.github;

import com.example.codereview.scm.ReviewPublication;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Publishes an Agent review back to a GitHub pull request as a PR comment and/or a Check Run.
 *
 * <p>The payload carries the summary, blocking findings, evidence links, the Agent Run URL, and the
 * patch validation state. Any publication that would expose generated patch content is refused
 * unless it has been approved — patches never reach a provider without a human in the loop.
 */
@Component
public class GitHubReviewPublisher {

    private final RestClient restClient;

    public GitHubReviewPublisher(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    /** Posts the review as an issue/PR comment. */
    public void publishComment(String apiBase, String token, String ownerRepo, int prNumber,
                               ReviewPublication publication, boolean approved) {
        requireApprovalForPatch(publication, approved);
        restClient.post()
                .uri(trimTrailingSlash(apiBase) + "/repos/" + ownerRepo + "/issues/" + prNumber + "/comments")
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .body(Map.of("body", renderCommentMarkdown(publication)))
                .retrieve()
                .toBodilessEntity();
    }

    /** Creates a completed Check Run for the head commit. */
    public void publishCheckRun(String apiBase, String token, String ownerRepo, String headSha,
                                ReviewPublication publication, boolean approved) {
        requireApprovalForPatch(publication, approved);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", "RepoSage Review");
        payload.put("head_sha", headSha);
        payload.put("status", "completed");
        payload.put("conclusion", checkConclusion(publication.conclusion()));
        payload.put("output", Map.of(
                "title", "RepoSage Review",
                "summary", renderCommentMarkdown(publication)));
        restClient.post()
                .uri(trimTrailingSlash(apiBase) + "/repos/" + ownerRepo + "/check-runs")
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
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

    private static String renderCommentMarkdown(ReviewPublication publication) {
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

    private static String checkConclusion(ReviewPublication.Conclusion conclusion) {
        return switch (conclusion) {
            case SUCCESS -> "success";
            case ACTION_REQUIRED -> "action_required";
            case NEUTRAL -> "neutral";
        };
    }

    private static String trimTrailingSlash(String value) {
        return value.replaceAll("/+$", "");
    }
}
