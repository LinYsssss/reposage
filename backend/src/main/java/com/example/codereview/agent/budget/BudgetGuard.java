package com.example.codereview.agent.budget;

import com.example.codereview.agent.error.AgentFailureType;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Guards Agent Run execution against budget limits.
 */
@Component
public class BudgetGuard {

    /**
     * Result of a budget check.
     */
    public static class BudgetCheckResult {
        private final boolean withinBudget;
        private final AgentFailureType failureType;
        private final String reason;

        private BudgetCheckResult(boolean withinBudget, AgentFailureType failureType, String reason) {
            this.withinBudget = withinBudget;
            this.failureType = failureType;
            this.reason = reason;
        }

        public static BudgetCheckResult ok() {
            return new BudgetCheckResult(true, null, null);
        }

        public static BudgetCheckResult exceeded(String reason) {
            return new BudgetCheckResult(false, AgentFailureType.BUDGET_EXCEEDED, reason);
        }

        public boolean isWithinBudget() {
            return withinBudget;
        }

        public AgentFailureType getFailureType() {
            return failureType;
        }

        public String getReason() {
            return reason;
        }
    }

    /**
     * Checks if the current usage is within budget.
     *
     * @param budget  the configured limits
     * @param usage   current usage
     * @param startedAt when the run started
     * @param now     current time
     * @return result indicating whether budget is exceeded
     */
    public BudgetCheckResult check(AgentBudget budget, BudgetUsage usage, Instant startedAt, Instant now) {
        // Check elapsed time
        if (budget.getMaxDurationSeconds() != null) {
            long elapsed = Duration.between(startedAt, now).getSeconds();
            if (elapsed > budget.getMaxDurationSeconds()) {
                return BudgetCheckResult.exceeded(
                        String.format("Elapsed time %ds exceeds limit %ds", elapsed, budget.getMaxDurationSeconds())
                );
            }
        }

        // Check tool calls
        if (budget.getMaxToolCalls() != null && usage.getToolCalls() > budget.getMaxToolCalls()) {
            return BudgetCheckResult.exceeded(
                    String.format("Tool calls %d exceeds limit %d", usage.getToolCalls(), budget.getMaxToolCalls())
            );
        }

        // Check model calls
        if (budget.getMaxModelCalls() != null && usage.getModelCalls() > budget.getMaxModelCalls()) {
            return BudgetCheckResult.exceeded(
                    String.format("Model calls %d exceeds limit %d", usage.getModelCalls(), budget.getMaxModelCalls())
            );
        }

        // Check input tokens
        if (budget.getMaxInputTokens() != null && usage.getInputTokens() > budget.getMaxInputTokens()) {
            return BudgetCheckResult.exceeded(
                    String.format("Input tokens %d exceeds limit %d", usage.getInputTokens(), budget.getMaxInputTokens())
            );
        }

        // Check output tokens
        if (budget.getMaxOutputTokens() != null && usage.getOutputTokens() > budget.getMaxOutputTokens()) {
            return BudgetCheckResult.exceeded(
                    String.format("Output tokens %d exceeds limit %d", usage.getOutputTokens(), budget.getMaxOutputTokens())
            );
        }

        // Check estimated cost
        if (budget.getMaxCostUsd() != null && usage.getCostUsd().compareTo(budget.getMaxCostUsd()) > 0) {
            return BudgetCheckResult.exceeded(
                    String.format("Cost $%.4f exceeds limit $%.4f", usage.getCostUsd(), budget.getMaxCostUsd())
            );
        }

        return BudgetCheckResult.ok();
    }
}
