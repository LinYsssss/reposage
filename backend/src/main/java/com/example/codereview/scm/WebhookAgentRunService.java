package com.example.codereview.scm;

import com.example.codereview.agent.outbox.AgentOutboxPublisher;
import com.example.codereview.agent.run.AgentRun;
import com.example.codereview.agent.run.AgentRunRepository;
import com.example.codereview.agent.run.AgentRunStatus;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a verified, normalized SCM event into an Agent Run.
 *
 * <p>Introduced for the webhook receive path (Task 3) and extended with supersede/idempotency
 * semantics in Task 5. A run is keyed by a deterministic {@link #triggerKey(NormalizedPullRequestEvent)}
 * (provider + installation + PR number + head SHA): the same event is idempotent (returns the
 * existing run, publishes nothing new), while a new head SHA yields a distinct key and a fresh run.
 */
@Service
public class WebhookAgentRunService {

    /** Outcome of {@link #startFromEvent}: the run plus whether it was newly created. */
    public record StartResult(AgentRun run, boolean created) {
    }

    private final AgentRunRepository agentRunRepository;
    private final AgentOutboxPublisher outboxPublisher;

    public WebhookAgentRunService(AgentRunRepository agentRunRepository,
                                  AgentOutboxPublisher outboxPublisher) {
        this.agentRunRepository = agentRunRepository;
        this.outboxPublisher = outboxPublisher;
    }

    @Transactional
    public StartResult startFromEvent(NormalizedPullRequestEvent event, ScmInstallation installation) {
        if (installation.getProjectId() == null || installation.getRepositoryId() == null) {
            throw new IllegalStateException("Installation is not bound to a project/repository");
        }
        String triggerKey = triggerKey(event);
        Optional<AgentRun> existing = agentRunRepository.findByTriggerKey(triggerKey);
        if (existing.isPresent()) {
            return new StartResult(existing.get(), false);
        }

        AgentRun run = new AgentRun();
        run.setTriggerKey(triggerKey);
        run.setProjectId(installation.getProjectId());
        run.setRepositoryId(installation.getRepositoryId());
        run.setHeadSha(event.headSha());
        run.setBaseSha(event.baseSha());
        run.setStatus(AgentRunStatus.RECEIVED);
        run.setStartedAt(Instant.now());
        run = agentRunRepository.save(run);

        // Transactional outbox: the run and its scheduling event commit together.
        outboxPublisher.publishStepEvent(run.getId(), "AGENT_RUN_RECEIVED", triggerKey);
        return new StartResult(run, true);
    }

    /** Deterministic, idempotent run key for a normalized event. */
    public static String triggerKey(NormalizedPullRequestEvent event) {
        return event.provider().name().toLowerCase()
                + ":" + event.installationRef()
                + ":pr:" + event.pullRequestNumber()
                + ":" + event.headSha();
    }
}
