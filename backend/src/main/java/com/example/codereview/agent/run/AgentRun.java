package com.example.codereview.agent.run;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Represents one persistent Agent execution for a PR review.
 * Transitions are managed by {@link AgentStateMachine}.
 */
@Entity
@Table(name = "agent_run")
public class AgentRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "trigger_key", nullable = false, unique = true)
    private String triggerKey;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "repository_id", nullable = false)
    private Long repositoryId;

    @Column(name = "pull_request_id")
    private Long pullRequestId;

    @Column(name = "head_sha", nullable = false, length = 80)
    private String headSha;

    @Column(name = "base_sha", length = 80)
    private String baseSha;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AgentRunStatus status;

    @Column(name = "current_step_sequence", nullable = false)
    private Integer currentStepSequence = 0;

    @Column(name = "cancellation_requested", nullable = false)
    private Boolean cancellationRequested = false;

    @Column(name = "failure_type", length = 64)
    private String failureType;

    @Column(name = "failure_message", columnDefinition = "TEXT")
    private String failureMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version = 0;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (startedAt == null) {
            startedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTriggerKey() {
        return triggerKey;
    }

    public void setTriggerKey(String triggerKey) {
        this.triggerKey = triggerKey;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getRepositoryId() {
        return repositoryId;
    }

    public void setRepositoryId(Long repositoryId) {
        this.repositoryId = repositoryId;
    }

    public Long getPullRequestId() {
        return pullRequestId;
    }

    public void setPullRequestId(Long pullRequestId) {
        this.pullRequestId = pullRequestId;
    }

    public String getHeadSha() {
        return headSha;
    }

    public void setHeadSha(String headSha) {
        this.headSha = headSha;
    }

    public String getBaseSha() {
        return baseSha;
    }

    public void setBaseSha(String baseSha) {
        this.baseSha = baseSha;
    }

    public AgentRunStatus getStatus() {
        return status;
    }

    public void setStatus(AgentRunStatus status) {
        this.status = status;
    }

    public Integer getCurrentStepSequence() {
        return currentStepSequence;
    }

    public void setCurrentStepSequence(Integer currentStepSequence) {
        this.currentStepSequence = currentStepSequence;
    }

    public Boolean getCancellationRequested() {
        return cancellationRequested;
    }

    public void setCancellationRequested(Boolean cancellationRequested) {
        this.cancellationRequested = cancellationRequested;
    }

    public String getFailureType() {
        return failureType;
    }

    public void setFailureType(String failureType) {
        this.failureType = failureType;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
