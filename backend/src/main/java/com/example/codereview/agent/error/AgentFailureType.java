package com.example.codereview.agent.error;

/**
 * Classified failure types for Agent execution.
 * Determines retry strategy and termination conditions.
 */
public enum AgentFailureType {
    /**
     * Transient external errors that may succeed on retry.
     * Examples: network timeout, model service 5xx, rate limit 429.
     */
    RETRYABLE_EXTERNAL_ERROR,

    /**
     * Model output failed JSON schema validation.
     */
    INVALID_MODEL_OUTPUT,

    /**
     * Tool execution returned an unexpected error.
     */
    TOOL_EXECUTION_FAILED,

    /**
     * Required dependencies or build environment missing.
     */
    ENVIRONMENT_INCOMPLETE,

    /**
     * Command, path, or permission violation.
     */
    SECURITY_POLICY_VIOLATION,

    /**
     * Time, token, cost, or call count exceeded.
     */
    BUDGET_EXCEEDED;

    public boolean isRetryable() {
        return this == RETRYABLE_EXTERNAL_ERROR;
    }
}
