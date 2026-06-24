package com.example.codereview.agent.outbox;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes Agent steps through transactional outbox.
 * State transition and outbox insertion commit or roll back together.
 */
@Service
public class AgentOutboxPublisher {

    private final AgentOutboxRepository outboxRepository;

    public AgentOutboxPublisher(AgentOutboxRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public void publishStepEvent(Long agentRunId, String eventType, String payload) {
        AgentOutboxEvent event = new AgentOutboxEvent();
        event.setAgentRunId(agentRunId);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setStatus("PENDING");
        outboxRepository.save(event);
    }
}
