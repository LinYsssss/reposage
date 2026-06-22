package com.example.codereview.agent.run;

/**
 * Legal states for an Agent Run, matching the approved design.
 * Transitions are enforced by {@link AgentStateMachine}.
 */
public enum AgentRunStatus {
    // Main path
    RECEIVED,
    PREPARING_REPOSITORY,
    ANALYZING_CHANGE,
    PLANNING,
    EXECUTING_TOOLS,
    RETRIEVING_CONTEXT,
    VERIFYING_FINDINGS,
    GENERATING_PATCH,
    VALIDATING_PATCH,
    WAITING_APPROVAL,
    PUBLISHING_RESULT,
    COMPLETED,

    // Exception states
    RETRY_WAIT,
    FAILED,
    CANCELED,
    TIMED_OUT;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELED || this == TIMED_OUT;
    }

    public boolean canRetry() {
        return this == RETRY_WAIT;
    }
}
