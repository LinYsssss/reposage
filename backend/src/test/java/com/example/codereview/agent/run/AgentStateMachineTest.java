package com.example.codereview.agent.run;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AgentStateMachineTest {

    private final AgentStateMachine stateMachine = new AgentStateMachine();

    @Test
    void mainPath_shouldAllowProgressiveTransitions() {
        // Main happy path
        assertThatNoException().isThrownBy(() -> {
            stateMachine.validateTransition(AgentRunStatus.RECEIVED, AgentRunStatus.PREPARING_REPOSITORY);
            stateMachine.validateTransition(AgentRunStatus.PREPARING_REPOSITORY, AgentRunStatus.ANALYZING_CHANGE);
            stateMachine.validateTransition(AgentRunStatus.ANALYZING_CHANGE, AgentRunStatus.PLANNING);
            stateMachine.validateTransition(AgentRunStatus.PLANNING, AgentRunStatus.EXECUTING_TOOLS);
            stateMachine.validateTransition(AgentRunStatus.EXECUTING_TOOLS, AgentRunStatus.RETRIEVING_CONTEXT);
            stateMachine.validateTransition(AgentRunStatus.RETRIEVING_CONTEXT, AgentRunStatus.VERIFYING_FINDINGS);
            stateMachine.validateTransition(AgentRunStatus.VERIFYING_FINDINGS, AgentRunStatus.GENERATING_PATCH);
            stateMachine.validateTransition(AgentRunStatus.GENERATING_PATCH, AgentRunStatus.VALIDATING_PATCH);
            stateMachine.validateTransition(AgentRunStatus.VALIDATING_PATCH, AgentRunStatus.WAITING_APPROVAL);
            stateMachine.validateTransition(AgentRunStatus.WAITING_APPROVAL, AgentRunStatus.PUBLISHING_RESULT);
            stateMachine.validateTransition(AgentRunStatus.PUBLISHING_RESULT, AgentRunStatus.COMPLETED);
        });
    }

    @Test
    void noFindings_shouldSkipPatchGeneration() {
        // No findings path: VERIFYING_FINDINGS -> PUBLISHING_RESULT
        assertThatNoException().isThrownBy(() ->
                stateMachine.validateTransition(AgentRunStatus.VERIFYING_FINDINGS, AgentRunStatus.PUBLISHING_RESULT)
        );
    }

    @Test
    void patchGenerationFailed_shouldSkipValidation() {
        // Patch generation failed: GENERATING_PATCH -> PUBLISHING_RESULT
        assertThatNoException().isThrownBy(() ->
                stateMachine.validateTransition(AgentRunStatus.GENERATING_PATCH, AgentRunStatus.PUBLISHING_RESULT)
        );
    }

    @Test
    void patchValidationFailed_shouldSkipApproval() {
        // Unsafe patch: VALIDATING_PATCH -> PUBLISHING_RESULT
        assertThatNoException().isThrownBy(() ->
                stateMachine.validateTransition(AgentRunStatus.VALIDATING_PATCH, AgentRunStatus.PUBLISHING_RESULT)
        );
    }

    @Test
    void illegalTransitions_shouldThrowException() {
        // Cannot go from COMPLETED to EXECUTING_TOOLS
        assertThatThrownBy(() ->
                stateMachine.validateTransition(AgentRunStatus.COMPLETED, AgentRunStatus.EXECUTING_TOOLS)
        ).isInstanceOf(IllegalAgentTransitionException.class)
                .hasMessageContaining("COMPLETED")
                .hasMessageContaining("EXECUTING_TOOLS");

        // Cannot go from CANCELED to RETRY_WAIT
        assertThatThrownBy(() ->
                stateMachine.validateTransition(AgentRunStatus.CANCELED, AgentRunStatus.RETRY_WAIT)
        ).isInstanceOf(IllegalAgentTransitionException.class);

        // Cannot go from WAITING_APPROVAL to COMPLETED (must go through PUBLISHING_RESULT)
        assertThatThrownBy(() ->
                stateMachine.validateTransition(AgentRunStatus.WAITING_APPROVAL, AgentRunStatus.COMPLETED)
        ).isInstanceOf(IllegalAgentTransitionException.class);
    }

    @Test
    void retryPath_shouldAllowReturnToAnyStep() {
        // RETRY_WAIT can transition back to any main step
        assertThatNoException().isThrownBy(() -> {
            stateMachine.validateTransition(AgentRunStatus.RETRY_WAIT, AgentRunStatus.PREPARING_REPOSITORY);
            stateMachine.validateTransition(AgentRunStatus.RETRY_WAIT, AgentRunStatus.PLANNING);
            stateMachine.validateTransition(AgentRunStatus.RETRY_WAIT, AgentRunStatus.EXECUTING_TOOLS);
        });
    }

    @Test
    void terminalStates_shouldHaveNoOutgoingTransitions() {
        assertThat(stateMachine.isTerminal(AgentRunStatus.COMPLETED)).isTrue();
        assertThat(stateMachine.isTerminal(AgentRunStatus.FAILED)).isTrue();
        assertThat(stateMachine.isTerminal(AgentRunStatus.CANCELED)).isTrue();
        assertThat(stateMachine.isTerminal(AgentRunStatus.TIMED_OUT)).isTrue();

        assertThat(stateMachine.getAllowedTransitions(AgentRunStatus.COMPLETED)).isEmpty();
        assertThat(stateMachine.getAllowedTransitions(AgentRunStatus.FAILED)).isEmpty();
        assertThat(stateMachine.getAllowedTransitions(AgentRunStatus.CANCELED)).isEmpty();
        assertThat(stateMachine.getAllowedTransitions(AgentRunStatus.TIMED_OUT)).isEmpty();
    }

    @Test
    void selfTransition_shouldBeIdempotent() {
        // Self-transition is always allowed (idempotent)
        assertThatNoException().isThrownBy(() -> {
            stateMachine.validateTransition(AgentRunStatus.RECEIVED, AgentRunStatus.RECEIVED);
            stateMachine.validateTransition(AgentRunStatus.PLANNING, AgentRunStatus.PLANNING);
            stateMachine.validateTransition(AgentRunStatus.COMPLETED, AgentRunStatus.COMPLETED);
        });
    }
}
