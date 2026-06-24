package com.example.codereview.agent.budget;

import java.math.BigDecimal;

/**
 * Current usage for an Agent Run execution.
 */
public class BudgetUsage {
    private long toolCalls;
    private long modelCalls;
    private long inputTokens;
    private long outputTokens;
    private BigDecimal costUsd;

    public BudgetUsage() {
        this.costUsd = BigDecimal.ZERO;
    }

    public void addToolCall() {
        this.toolCalls++;
    }

    public void addModelCall(long inputTokens, long outputTokens, BigDecimal cost) {
        this.modelCalls++;
        this.inputTokens += inputTokens;
        this.outputTokens += outputTokens;
        this.costUsd = this.costUsd.add(cost);
    }

    public long getToolCalls() {
        return toolCalls;
    }

    public long getModelCalls() {
        return modelCalls;
    }

    public long getInputTokens() {
        return inputTokens;
    }

    public long getOutputTokens() {
        return outputTokens;
    }

    public BigDecimal getCostUsd() {
        return costUsd;
    }
}
