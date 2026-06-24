package com.example.codereview.agent.run;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Deterministic state machine for Agent Run transitions.
 * Enforces the approved design state graph.
 */
@Component
public class AgentStateMachine {

    private static final Map<AgentRunStatus, Set<AgentRunStatus>> TRANSITIONS = Map.ofEntries(
            // Main path
            Map.entry(AgentRunStatus.RECEIVED, Set.of(
                    AgentRunStatus.PREPARING_REPOSITORY,
                    AgentRunStatus.RETRY_WAIT,
                    AgentRunStatus.FAILED,
                    AgentRunStatus.CANCELED
            )),
            Map.entry(AgentRunStatus.PREPARING_REPOSITORY, Set.of(
                    AgentRunStatus.ANALYZING_CHANGE,
                    AgentRunStatus.RETRY_WAIT,
                    AgentRunStatus.FAILED,
                    AgentRunStatus.CANCELED
            )),
            Map.entry(AgentRunStatus.ANALYZING_CHANGE, Set.of(
                    AgentRunStatus.PLANNING,
                    AgentRunStatus.RETRY_WAIT,
                    AgentRunStatus.FAILED,
                    AgentRunStatus.CANCELED
            )),
            Map.entry(AgentRunStatus.PLANNING, Set.of(
                    AgentRunStatus.EXECUTING_TOOLS,
                    AgentRunStatus.RETRY_WAIT,
                    AgentRunStatus.FAILED,
                    AgentRunStatus.CANCELED
            )),
            Map.entry(AgentRunStatus.EXECUTING_TOOLS, Set.of(
                    AgentRunStatus.RETRIEVING_CONTEXT,
                    AgentRunStatus.RETRY_WAIT,
                    AgentRunStatus.FAILED,
                    AgentRunStatus.CANCELED
            )),
            Map.entry(AgentRunStatus.RETRIEVING_CONTEXT, Set.of(
                    AgentRunStatus.VERIFYING_FINDINGS,
                    AgentRunStatus.RETRY_WAIT,
                    AgentRunStatus.FAILED,
                    AgentRunStatus.CANCELED
            )),
            Map.entry(AgentRunStatus.VERIFYING_FINDINGS, Set.of(
                    AgentRunStatus.GENERATING_PATCH,
                    AgentRunStatus.PUBLISHING_RESULT,  // No findings, skip patch
                    AgentRunStatus.RETRY_WAIT,
                    AgentRunStatus.FAILED,
                    AgentRunStatus.CANCELED
            )),
            Map.entry(AgentRunStatus.GENERATING_PATCH, Set.of(
                    AgentRunStatus.VALIDATING_PATCH,
                    AgentRunStatus.PUBLISHING_RESULT,  // Patch generation failed or not applicable
                    AgentRunStatus.RETRY_WAIT,
                    AgentRunStatus.FAILED,
                    AgentRunStatus.CANCELED
            )),
            Map.entry(AgentRunStatus.VALIDATING_PATCH, Set.of(
                    AgentRunStatus.WAITING_APPROVAL,
                    AgentRunStatus.PUBLISHING_RESULT,  // Validation failed, unsafe to approve
                    AgentRunStatus.RETRY_WAIT,
                    AgentRunStatus.FAILED,
                    AgentRunStatus.CANCELED
            )),
            Map.entry(AgentRunStatus.WAITING_APPROVAL, Set.of(
                    AgentRunStatus.PUBLISHING_RESULT,  // Approved or rejected
                    AgentRunStatus.CANCELED
            )),
            Map.entry(AgentRunStatus.PUBLISHING_RESULT, Set.of(
                    AgentRunStatus.COMPLETED,
                    AgentRunStatus.RETRY_WAIT,
                    AgentRunStatus.FAILED,
                    AgentRunStatus.CANCELED
            )),

            // Retry path
            Map.entry(AgentRunStatus.RETRY_WAIT, Set.of(
                    AgentRunStatus.PREPARING_REPOSITORY,
                    AgentRunStatus.ANALYZING_CHANGE,
                    AgentRunStatus.PLANNING,
                    AgentRunStatus.EXECUTING_TOOLS,
                    AgentRunStatus.RETRIEVING_CONTEXT,
                    AgentRunStatus.VERIFYING_FINDINGS,
                    AgentRunStatus.GENERATING_PATCH,
                    AgentRunStatus.VALIDATING_PATCH,
                    AgentRunStatus.PUBLISHING_RESULT,
                    AgentRunStatus.FAILED,
                    AgentRunStatus.CANCELED
            )),

            // Terminal states have no outgoing transitions
            Map.entry(AgentRunStatus.COMPLETED, Set.of()),
            Map.entry(AgentRunStatus.FAILED, Set.of()),
            Map.entry(AgentRunStatus.CANCELED, Set.of()),
            Map.entry(AgentRunStatus.TIMED_OUT, Set.of())
    );

    /**
     * Validates whether a transition from one status to another is legal.
     *
     * @param from current status
     * @param to   target status
     * @throws IllegalAgentTransitionException if the transition is not allowed
     */
    public void validateTransition(AgentRunStatus from, AgentRunStatus to) {
        if (from == to) {
            return; // Self-transition is idempotent
        }

        Set<AgentRunStatus> allowedTargets = TRANSITIONS.get(from);
        if (allowedTargets == null || !allowedTargets.contains(to)) {
            throw new IllegalAgentTransitionException(from, to);
        }
    }

    /**
     * Checks if a status is terminal (no further transitions allowed).
     */
    public boolean isTerminal(AgentRunStatus status) {
        return status.isTerminal();
    }

    /**
     * Returns the set of valid target statuses from the given status.
     */
    public Set<AgentRunStatus> getAllowedTransitions(AgentRunStatus from) {
        return TRANSITIONS.getOrDefault(from, Set.of());
    }
}
