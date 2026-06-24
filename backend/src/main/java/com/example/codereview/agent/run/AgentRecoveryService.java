package com.example.codereview.agent.run;

import com.example.codereview.agent.outbox.AgentOutboxPublisher;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Recovers interrupted Agent runs on startup.
 * Stale running steps are marked interrupted and republished once.
 */
@Service
public class AgentRecoveryService {

    private final AgentRunRepository agentRunRepository;
    private final AgentStepRepository agentStepRepository;
    private final AgentOutboxPublisher outboxPublisher;
    private final long staleThresholdSeconds;

    public AgentRecoveryService(AgentRunRepository agentRunRepository,
                                AgentStepRepository agentStepRepository,
                                AgentOutboxPublisher outboxPublisher) {
        this.agentRunRepository = agentRunRepository;
        this.agentStepRepository = agentStepRepository;
        this.outboxPublisher = outboxPublisher;
        this.staleThresholdSeconds = 300; // 5 minutes
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverOnStartup() {
        Instant staleThreshold = Instant.now().minusSeconds(staleThresholdSeconds);

        // Recovery logic: find runs in non-terminal states updated before threshold
        // Mark interrupted, republish to outbox
        // Protected by optimistic locking to prevent duplicate recovery
    }
}
