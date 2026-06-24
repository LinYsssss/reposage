package com.example.codereview.agent.api;

import com.example.codereview.agent.run.AgentRunRepository;
import com.example.codereview.agent.run.AgentStepRepository;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Exposes Agent run timeline and SSE event stream.
 * Returns persisted state first; SSE only as notification channel.
 */
@RestController
@RequestMapping("/api/agent-runs")
public class AgentRunController {

    private final AgentRunRepository agentRunRepository;
    private final AgentStepRepository agentStepRepository;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public AgentRunController(AgentRunRepository agentRunRepository,
                              AgentStepRepository agentStepRepository) {
        this.agentRunRepository = agentRunRepository;
        this.agentStepRepository = agentStepRepository;
    }

    @GetMapping("/{runId}")
    public Object getRunDetail(@PathVariable Long runId) {
        var run = agentRunRepository.findById(runId).orElseThrow();
        var steps = agentStepRepository.findByAgentRunIdOrderBySequenceNoAsc(runId);
        return new AgentRunDetail(run, steps);
    }

    @GetMapping(value = "/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(@PathVariable Long runId,
                                     @RequestParam(required = false) String lastEventId) {
        SseEmitter emitter = new SseEmitter(300_000L);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    private record AgentRunDetail(Object run, Object steps) {
    }
}
