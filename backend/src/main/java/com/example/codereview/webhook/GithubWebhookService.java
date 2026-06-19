package com.example.codereview.webhook;

import com.example.codereview.project.ProjectEntity;
import com.example.codereview.project.ProjectRepository;
import com.example.codereview.pullrequest.PullRequestEntity;
import com.example.codereview.pullrequest.PullRequestRepository;
import com.example.codereview.repo.CodeRepositoryEntity;
import com.example.codereview.repo.CodeRepositoryJpaRepository;
import com.example.codereview.review.ReviewDtos.CreateReviewTaskRequest;
import com.example.codereview.review.ReviewDtos.ReviewTaskResponse;
import com.example.codereview.review.ReviewService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns an inbound GitHub {@code pull_request} webhook into a review: resolves the bound repository,
 * upserts the {@link PullRequestEntity}, and triggers a review task via {@link ReviewService} as the
 * project owner. Honours the existing inline/async review mode. Unmatched repos / ignored actions are
 * no-ops so the endpoint stays demo-safe without a real GitHub integration.
 */
@Service
public class GithubWebhookService {

    private static final Logger log = LoggerFactory.getLogger(GithubWebhookService.class);
    private static final Set<String> REVIEWABLE_ACTIONS = Set.of("opened", "synchronize", "reopened", "ready_for_review");

    private final ObjectMapper objectMapper;
    private final CodeRepositoryJpaRepository repositories;
    private final PullRequestRepository pullRequests;
    private final ProjectRepository projects;
    private final ReviewService reviewService;

    public GithubWebhookService(ObjectMapper objectMapper, CodeRepositoryJpaRepository repositories,
                                PullRequestRepository pullRequests, ProjectRepository projects,
                                ReviewService reviewService) {
        this.objectMapper = objectMapper;
        this.repositories = repositories;
        this.pullRequests = pullRequests;
        this.projects = projects;
        this.reviewService = reviewService;
    }

    @Transactional
    public String handle(String event, String payload) {
        if (!"pull_request".equals(event)) {
            return "ignored event: " + event;
        }
        JsonNode root = parse(payload);
        String action = root.path("action").asText("");
        if (!REVIEWABLE_ACTIONS.contains(action)) {
            return "ignored action: " + action;
        }
        JsonNode pr = root.path("pull_request");
        String fullName = root.path("repository").path("full_name").asText("");
        CodeRepositoryEntity repository = resolveRepository(fullName);
        if (repository == null) {
            log.info("Webhook: 未找到匹配仓库 {}", fullName);
            return "no matching repository: " + fullName;
        }
        ProjectEntity project = projects.findById(repository.getProjectId()).orElse(null);
        if (project == null) {
            return "no project for repository " + repository.getId();
        }

        int number = pr.path("number").asInt();
        String externalId = fullName + "#" + number;
        PullRequestEntity entity = pullRequests.findByRepositoryIdAndPrNumber(repository.getId(), number).orElse(null);
        if (entity == null) {
            entity = new PullRequestEntity(
                    project.getId(), repository.getId(), "GITHUB", externalId, number,
                    text(pr, "title", "PR #" + number), pr.path("user").path("login").asText(null),
                    pr.path("head").path("ref").asText(""), pr.path("base").path("ref").asText(""),
                    pr.path("base").path("sha").asText(""), pr.path("head").path("sha").asText(""));
        } else {
            entity.update(number, text(pr, "title", "PR #" + number), pr.path("user").path("login").asText(null),
                    pr.path("head").path("ref").asText(""), pr.path("base").path("ref").asText(""),
                    pr.path("base").path("sha").asText(""), pr.path("head").path("sha").asText(""),
                    "GITHUB", externalId, "OPEN");
        }
        PullRequestEntity saved = pullRequests.save(entity);

        ReviewTaskResponse task = reviewService.create(project.getId(), project.getOwnerId(),
                new CreateReviewTaskRequest(null, null, null, null, saved.getId()));
        log.info("Webhook: {} {} -> 审查任务 #{}", action, externalId, task.taskId());
        return "review task " + task.taskId() + " for " + externalId;
    }

    private CodeRepositoryEntity resolveRepository(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return null;
        }
        List<CodeRepositoryEntity> matches = repositories.findByRepoUrlContaining(fullName);
        return matches.stream()
                .filter(r -> "ACTIVE".equals(r.getStatus()))
                .findFirst()
                .orElse(matches.isEmpty() ? null : matches.get(0));
    }

    private JsonNode parse(String payload) {
        try {
            return objectMapper.readTree(payload == null ? "{}" : payload);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Webhook payload 不是合法 JSON");
        }
    }

    private String text(JsonNode node, String field, String fallback) {
        String value = node.path(field).asText("");
        return value.isBlank() ? fallback : value;
    }
}
