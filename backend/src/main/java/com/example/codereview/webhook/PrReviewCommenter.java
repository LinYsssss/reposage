package com.example.codereview.webhook;

import com.example.codereview.common.security.CryptoService;
import com.example.codereview.pullrequest.PullRequestEntity;
import com.example.codereview.pullrequest.PullRequestRepository;
import com.example.codereview.repo.CodeRepositoryEntity;
import com.example.codereview.repo.CodeRepositoryJpaRepository;
import com.example.codereview.report.ReviewIssue;
import com.example.codereview.report.ReviewIssueRepository;
import com.example.codereview.report.ReviewReport;
import com.example.codereview.report.ReviewReportRepository;
import com.example.codereview.review.ReviewTask;
import com.example.codereview.review.ReviewTaskRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Posts a finished review report back to its GitHub pull request as a comment. Only acts when the
 * task is linked to a GITHUB PR with a stored (decryptable) access token and write-back is enabled;
 * otherwise it logs and returns. All failures are swallowed so a comment problem never fails the
 * review itself.
 */
@Service
public class PrReviewCommenter {

    private static final Logger log = LoggerFactory.getLogger(PrReviewCommenter.class);
    private static final int MAX_ISSUES_IN_COMMENT = 25;

    private final ReviewTaskRepository tasks;
    private final PullRequestRepository pullRequests;
    private final CodeRepositoryJpaRepository repositories;
    private final ReviewReportRepository reports;
    private final ReviewIssueRepository issues;
    private final CryptoService cryptoService;
    private final boolean enabled;
    private final String apiBase;
    private final RestClient restClient;

    public PrReviewCommenter(ReviewTaskRepository tasks, PullRequestRepository pullRequests,
                             CodeRepositoryJpaRepository repositories, ReviewReportRepository reports,
                             ReviewIssueRepository issues, CryptoService cryptoService,
                             RestClient.Builder restClientBuilder,
                             @Value("${app.webhook.post-comments:true}") boolean enabled,
                             @Value("${app.webhook.github.api-base:https://api.github.com}") String apiBase) {
        this.tasks = tasks;
        this.pullRequests = pullRequests;
        this.repositories = repositories;
        this.reports = reports;
        this.issues = issues;
        this.cryptoService = cryptoService;
        this.enabled = enabled;
        this.apiBase = apiBase.replaceAll("/+$", "");
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = restClientBuilder.requestFactory(factory).build();
    }

    public void onReviewCompleted(Long taskId) {
        if (!enabled) {
            return;
        }
        try {
            ReviewTask task = tasks.findById(taskId).orElse(null);
            if (task == null || task.getPullRequestId() == null) {
                return;
            }
            PullRequestEntity pr = pullRequests.findById(task.getPullRequestId()).orElse(null);
            if (pr == null || !"GITHUB".equals(pr.getProvider()) || pr.getPrNumber() == null) {
                return;
            }
            CodeRepositoryEntity repo = repositories.findById(pr.getRepositoryId()).orElse(null);
            if (repo == null) {
                return;
            }
            String token = decryptToken(repo);
            String ownerRepo = parseOwnerRepo(repo.getRepoUrl());
            if (token == null || ownerRepo == null) {
                log.info("PR {} 缺少 token 或无法解析 owner/repo，跳过评论回写", pr.getExternalPrId());
                return;
            }
            ReviewReport report = reports.findByTaskId(taskId).orElse(null);
            if (report == null) {
                return;
            }
            postComment(ownerRepo, pr.getPrNumber(), renderComment(report), token);
            log.info("已回写 PR 评论: {} #{}", ownerRepo, pr.getPrNumber());
        } catch (Exception ex) {
            log.warn("PR 评论回写失败 (task {}): {}", taskId, ex.toString());
        }
    }

    private void postComment(String ownerRepo, Integer number, String body, String token) {
        restClient.post()
                .uri(apiBase + "/repos/" + ownerRepo + "/issues/" + number + "/comments")
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .body(Map.of("body", body))
                .retrieve()
                .toBodilessEntity();
    }

    private String renderComment(ReviewReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 🤖 RepoSage 自动代码审查\n\n");
        sb.append("**整体风险：").append(report.getOverallRisk()).append("** · 发现 ")
                .append(report.getIssueCount()).append(" 个问题 · commit `")
                .append(abbreviate(report.getCommitId(), 10)).append("`\n\n");
        if (report.getSummary() != null && !report.getSummary().isBlank()) {
            sb.append("> ").append(report.getSummary().replace("\n", "\n> ")).append("\n\n");
        }
        List<ReviewIssue> list = issues.findByReportId(report.getId());
        if (!list.isEmpty()) {
            sb.append("| 级别 | 类别 | 位置 | 问题 |\n|---|---|---|---|\n");
            list.stream().limit(MAX_ISSUES_IN_COMMENT).forEach(i -> sb.append("| ")
                    .append(i.getSeverity()).append(" | ").append(i.getCategory()).append(" | ")
                    .append(location(i)).append(" | ").append(escapeCell(i.getTitle())).append(" |\n"));
            if (list.size() > MAX_ISSUES_IN_COMMENT) {
                sb.append("\n_仅显示前 ").append(MAX_ISSUES_IN_COMMENT).append(" 条，共 ")
                        .append(list.size()).append(" 条。_\n");
            }
        }
        sb.append("\n<sub>由 RepoSage 自动生成</sub>");
        return sb.toString();
    }

    private String location(ReviewIssue issue) {
        if (issue.getFilePath() == null) {
            return "-";
        }
        return issue.getLineStart() == null ? issue.getFilePath() : issue.getFilePath() + ":" + issue.getLineStart();
    }

    private String escapeCell(String value) {
        return value == null ? "" : value.replace("|", "\\|").replace("\n", " ");
    }

    private String abbreviate(String value, int len) {
        if (value == null) {
            return "";
        }
        return value.length() <= len ? value : value.substring(0, len);
    }

    private String decryptToken(CodeRepositoryEntity repo) {
        String cipher = repo.getAccessTokenCiphertext();
        if (cipher == null || cipher.isBlank()) {
            return null;
        }
        try {
            return cryptoService.decrypt(cipher);
        } catch (Exception ex) {
            return null;
        }
    }

    /** Extracts {@code owner/repo} from an https or ssh GitHub URL. */
    private String parseOwnerRepo(String repoUrl) {
        if (repoUrl == null || repoUrl.isBlank()) {
            return null;
        }
        String s = repoUrl.trim()
                .replaceFirst("^[a-zA-Z]+://", "")
                .replaceFirst("^git@", "")
                .replace(":", "/")
                .replaceFirst("\\.git$", "");
        int idx = s.indexOf("github.com/");
        if (idx >= 0) {
            s = s.substring(idx + "github.com/".length());
        }
        String[] parts = s.split("/");
        return parts.length >= 2 && !parts[0].isBlank() && !parts[1].isBlank()
                ? parts[0] + "/" + parts[1]
                : null;
    }
}
