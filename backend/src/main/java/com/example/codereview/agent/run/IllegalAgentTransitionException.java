package com.example.codereview.agent.run;

/**
 * Thrown when an illegal Agent Run state transition is attempted.
 */
public class IllegalAgentTransitionException extends RuntimeException {

    private final AgentRunStatus fromStatus;
    private final AgentRunStatus toStatus;

    public IllegalAgentTransitionException(AgentRunStatus fromStatus, AgentRunStatus toStatus) {
        super(String.format("Illegal state transition from %s to %s", fromStatus, toStatus));
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
    }

    public AgentRunStatus getFromStatus() {
        return fromStatus;
    }

    public AgentRunStatus getToStatus() {
        return toStatus;
    }
}
