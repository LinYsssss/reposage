package com.example.codereview.agent.compat;

import com.example.codereview.agent.run.AgentRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projects completed Agent Runs into legacy review_report format.
 * Preserves frontend compatibility during migration.
 */
@Service
public class LegacyReviewProjectionService {

    private final AgentRunRepository agentRunRepository;

    public LegacyReviewProjectionService(AgentRunRepository agentRunRepository) {
        this.agentRunRepository = agentRunRepository;
    }

    @Transactional
    public void projectToLegacyReport(Long agentRunId) {
        // One completed Agent Run creates one legacy report
        // Repeated projection is idempotent via unique projection key
        // Converts agent findings to review_issue format
        // Implementation deferred until review_report entity available
    }
}
