package com.example.codereview.agent.run;

/**
 * Status of an individual Agent step execution.
 */
public enum AgentStepStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED,
    SKIPPED,
    CANCELED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == SKIPPED || this == CANCELED;
    }
}
