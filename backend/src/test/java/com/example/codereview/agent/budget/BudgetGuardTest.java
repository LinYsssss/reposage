package com.example.codereview.agent.budget;

import com.example.codereview.agent.error.AgentFailureType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

class BudgetGuardTest {

    private final BudgetGuard guard = new BudgetGuard();

    @Test
    void withinAllLimits_shouldReturnOk() {
        AgentBudget budget = new AgentBudget(
                300L,  // 5 minutes
                50,
                10,
                100_000L,
                50_000L,
                new BigDecimal("1.00")
        );

        BudgetUsage usage = new BudgetUsage();
        usage.addToolCall();
        usage.addModelCall(1000, 500, new BigDecimal("0.01"));

        Instant start = Instant.now().minusSeconds(60);
        Instant now = Instant.now();

        var result = guard.check(budget, usage, start, now);

        assertThat(result.isWithinBudget()).isTrue();
        assertThat(result.getFailureType()).isNull();
    }

    @Test
    void elapsedTimeExceeded_shouldReturnBudgetExceeded() {
        AgentBudget budget = new AgentBudget(60L, null, null, null, null, null);
        BudgetUsage usage = new BudgetUsage();

        Instant start = Instant.now().minusSeconds(120);
        Instant now = Instant.now();

        var result = guard.check(budget, usage, start, now);

        assertThat(result.isWithinBudget()).isFalse();
        assertThat(result.getFailureType()).isEqualTo(AgentFailureType.BUDGET_EXCEEDED);
        assertThat(result.getReason()).contains("Elapsed time");
    }

    @Test
    void toolCallsExceeded_shouldReturnBudgetExceeded() {
        AgentBudget budget = new AgentBudget(null, 5, null, null, null, null);
        BudgetUsage usage = new BudgetUsage();
        for (int i = 0; i < 6; i++) {
            usage.addToolCall();
        }

        var result = guard.check(budget, usage, Instant.now(), Instant.now());

        assertThat(result.isWithinBudget()).isFalse();
        assertThat(result.getFailureType()).isEqualTo(AgentFailureType.BUDGET_EXCEEDED);
        assertThat(result.getReason()).contains("Tool calls");
    }

    @Test
    void modelCallsExceeded_shouldReturnBudgetExceeded() {
        AgentBudget budget = new AgentBudget(null, null, 3, null, null, null);
        BudgetUsage usage = new BudgetUsage();
        for (int i = 0; i < 4; i++) {
            usage.addModelCall(100, 50, BigDecimal.ZERO);
        }

        var result = guard.check(budget, usage, Instant.now(), Instant.now());

        assertThat(result.isWithinBudget()).isFalse();
        assertThat(result.getFailureType()).isEqualTo(AgentFailureType.BUDGET_EXCEEDED);
        assertThat(result.getReason()).contains("Model calls");
    }

    @Test
    void inputTokensExceeded_shouldReturnBudgetExceeded() {
        AgentBudget budget = new AgentBudget(null, null, null, 1000L, null, null);
        BudgetUsage usage = new BudgetUsage();
        usage.addModelCall(1500, 100, BigDecimal.ZERO);

        var result = guard.check(budget, usage, Instant.now(), Instant.now());

        assertThat(result.isWithinBudget()).isFalse();
        assertThat(result.getFailureType()).isEqualTo(AgentFailureType.BUDGET_EXCEEDED);
        assertThat(result.getReason()).contains("Input tokens");
    }

    @Test
    void outputTokensExceeded_shouldReturnBudgetExceeded() {
        AgentBudget budget = new AgentBudget(null, null, null, null, 500L, null);
        BudgetUsage usage = new BudgetUsage();
        usage.addModelCall(100, 600, BigDecimal.ZERO);

        var result = guard.check(budget, usage, Instant.now(), Instant.now());

        assertThat(result.isWithinBudget()).isFalse();
        assertThat(result.getFailureType()).isEqualTo(AgentFailureType.BUDGET_EXCEEDED);
        assertThat(result.getReason()).contains("Output tokens");
    }

    @Test
    void costExceeded_shouldReturnBudgetExceeded() {
        AgentBudget budget = new AgentBudget(null, null, null, null, null, new BigDecimal("0.50"));
        BudgetUsage usage = new BudgetUsage();
        usage.addModelCall(10000, 5000, new BigDecimal("0.60"));

        var result = guard.check(budget, usage, Instant.now(), Instant.now());

        assertThat(result.isWithinBudget()).isFalse();
        assertThat(result.getFailureType()).isEqualTo(AgentFailureType.BUDGET_EXCEEDED);
        assertThat(result.getReason()).contains("Cost");
    }
}
