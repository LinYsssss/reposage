package com.example.codereview.agent.queue;

import com.example.codereview.agent.run.AgentRunRepository;
import com.example.codereview.config.RabbitMqConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes Agent step messages from RabbitMQ.
 * Uses (agentRunId, sequenceNo, attempt) as idempotency key.
 */
@Component
public class AgentStepConsumer {

    private final AgentRunRepository agentRunRepository;

    public AgentStepConsumer(AgentRunRepository agentRunRepository) {
        this.agentRunRepository = agentRunRepository;
    }

    @RabbitListener(queues = "${app.agent.queue-name:agent.step.work}")
    public void consume(AgentStepMessage message) {
        // Idempotent processing: check if this (runId, sequence, attempt) already completed
        // Execute step, update agent_run status via state machine
        // On success: mark step completed, advance to next state
        // On retryable error: publish to delay queue with incremented attempt
        // On terminal error: mark run as FAILED
    }
}
