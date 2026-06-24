package com.example.codereview.agent.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Agent metrics instrumentation.
 * Tags only bounded values (status, tool name), never IDs or error messages.
 */
@Component
public class AgentMetrics {

    private final MeterRegistry meterRegistry;

    public AgentMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void recordRunCreated(String status) {
        Counter.builder("agent.run.created")
                .tag("status", status)
                .register(meterRegistry)
                .increment();
    }

    public void recordRunCompleted(String status, long durationMs) {
        Timer.builder("agent.run.duration")
                .tag("status", status)
                .register(meterRegistry)
                .record(java.time.Duration.ofMillis(durationMs));
    }

    public void recordStepExecution(String stepType, String status, long durationMs) {
        Timer.builder("agent.step.duration")
                .tag("step_type", stepType)
                .tag("status", status)
                .register(meterRegistry)
                .record(java.time.Duration.ofMillis(durationMs));
    }

    public void recordToolInvocation(String toolName, boolean success, long durationMs) {
        Timer.builder("agent.tool.duration")
                .tag("tool", toolName)
                .tag("success", String.valueOf(success))
                .register(meterRegistry)
                .record(java.time.Duration.ofMillis(durationMs));
    }
}
