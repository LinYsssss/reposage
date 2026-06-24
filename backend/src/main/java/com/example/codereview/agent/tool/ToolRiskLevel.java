package com.example.codereview.agent.tool;

/**
 * Risk level for a tool, determines approval requirements.
 */
public enum ToolRiskLevel {
    /**
     * Read-only operations with no side effects.
     */
    READ_ONLY,

    /**
     * Operations executed in isolated sandbox.
     */
    SANDBOXED,

    /**
     * Generative operations producing content.
     */
    GENERATIVE,

    /**
     * Operations requiring human approval before execution.
     */
    APPROVAL_REQUIRED
}
