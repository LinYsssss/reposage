package com.example.codereview.agent.budget;

import java.math.BigDecimal;

/**
 * Budget limits for an Agent Run execution.
 */
public class AgentBudget {
    private final Long maxDurationSeconds;
    private final Integer maxToolCalls;
    private final Integer maxModelCalls;
    private final Long maxInputTokens;
    private final Long maxOutputTokens;
    private final BigDecimal maxCostUsd;

    public AgentBudget(Long maxDurationSeconds, Integer maxToolCalls, Integer maxModelCalls,
                       Long maxInputTokens, Long maxOutputTokens, BigDecimal maxCostUsd) {
        this.maxDurationSeconds = maxDurationSeconds;
        this.maxToolCalls = maxToolCalls;
        this.maxModelCalls = maxModelCalls;
        this.maxInputTokens = maxInputTokens;
        this.maxOutputTokens = maxOutputTokens;
        this.maxCostUsd = maxCostUsd;
    }

    public Long getMaxDurationSeconds() {
        return maxDurationSeconds;
    }

    public Integer getMaxToolCalls() {
        return maxToolCalls;
    }

    public Integer getMaxModelCalls() {
        return maxModelCalls;
    }

    public Long getMaxInputTokens() {
        return maxInputTokens;
    }

    public Long getMaxOutputTokens() {
        return maxOutputTokens;
    }

    public BigDecimal getMaxCostUsd() {
        return maxCostUsd;
    }
}
