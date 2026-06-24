package com.example.codereview.agent.queue;

/**
 * Message format for Agent step scheduling.
 */
public class AgentStepMessage {
    private Long agentRunId;
    private Integer sequenceNo;
    private Integer attempt;

    public AgentStepMessage() {
    }

    public AgentStepMessage(Long agentRunId, Integer sequenceNo, Integer attempt) {
        this.agentRunId = agentRunId;
        this.sequenceNo = sequenceNo;
        this.attempt = attempt;
    }

    public Long getAgentRunId() {
        return agentRunId;
    }

    public void setAgentRunId(Long agentRunId) {
        this.agentRunId = agentRunId;
    }

    public Integer getSequenceNo() {
        return sequenceNo;
    }

    public void setSequenceNo(Integer sequenceNo) {
        this.sequenceNo = sequenceNo;
    }

    public Integer getAttempt() {
        return attempt;
    }

    public void setAttempt(Integer attempt) {
        this.attempt = attempt;
    }
}
