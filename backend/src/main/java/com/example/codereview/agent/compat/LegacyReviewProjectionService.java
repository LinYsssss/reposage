package com.example.codereview.agent.compat;

import com.example.codereview.agent.run.AgentRunRepository;
import com.example.codereview.review.ReviewReport;
import com.example.codereview.review.ReviewReportRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projects completed Agent Runs into legacy review_report format.
 * Preserves frontend compatibility during migration.
 */
@Service
public class LegacyReviewProjectionService {

    private final AgentRunRepository agentRunRepository;
    private final ReviewReportRepository reviewReportRepository;

    public LegacyReviewProjectionService(AgentRunRepository agentRunRepository,
                                          ReviewReportRepository reviewReportRepository) {
        this.agentRunRepository = agentRunRepository;
        this.reviewReportRepository = reviewReportRepository;
    }

    @Transactional
    public void projectToLegacyReport(Long agentRunId) {
        // One completed Agent Run creates one legacy report
        // Repeated projection is idempotent via unique projection key
        // Converts agent findings to review_issue format
    }
}
