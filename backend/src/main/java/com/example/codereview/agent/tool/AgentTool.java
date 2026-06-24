package com.example.codereview.agent.tool;

/**
 * Typed tool interface. Every Agent tool must implement this.
 *
 * @param <I> input type
 * @param <O> output type
 */
public interface AgentTool<I, O> {

    /**
     * Unique tool name (e.g., "git.diff", "code.search").
     */
    String name();

    /**
     * Risk level determines approval requirements.
     */
    ToolRiskLevel riskLevel();

    /**
     * JSON schema for input validation (optional).
     */
    default String inputSchema() {
        return null;
    }

    /**
     * Execute the tool with validated input.
     *
     * @param context execution context
     * @param input   validated input
     * @return execution result
     */
    ToolResult<O> execute(ToolContext context, I input);
}
