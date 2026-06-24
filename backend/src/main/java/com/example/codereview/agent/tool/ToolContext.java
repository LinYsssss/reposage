package com.example.codereview.agent.tool;

import java.time.Instant;

/**
 * Execution context provided to every tool invocation.
 */
public class ToolContext {
    private final Long agentRunId;
    private final Long stepId;
    private final Long projectId;
    private final Long repositoryId;
    private final String commitSha;
    private final String workspaceId;

    public ToolContext(Long agentRunId, Long stepId, Long projectId, Long repositoryId,
                       String commitSha, String workspaceId) {
        this.agentRunId = agentRunId;
        this.stepId = stepId;
        this.projectId = projectId;
        this.repositoryId = repositoryId;
        this.commitSha = commitSha;
        this.workspaceId = workspaceId;
    }

    public Long getAgentRunId() {
        return agentRunId;
    }

    public Long getStepId() {
        return stepId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getRepositoryId() {
        return repositoryId;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }
}
