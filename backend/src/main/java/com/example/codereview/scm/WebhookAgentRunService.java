package com.example.codereview.scm;

import com.example.codereview.agent.outbox.AgentOutboxPublisher;
import com.example.codereview.agent.run.AgentRun;
import com.example.codereview.agent.run.AgentRunRepository;
import com.example.codereview.agent.run.AgentRunStatus;
import com.example.codereview.agent.run.AgentStateMachine;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns a verified, normalized SCM event into an Agent Run.
 *
 * <p>A run is keyed by a deterministic {@link #triggerKey(NormalizedPullRequestEvent)} (provider +
 * installation + PR number + head SHA):
 * <ul>
 *   <li>The same event is idempotent — it returns the existing run and publishes nothing new.</li>
 *   <li>A new head SHA yields a distinct key and a fresh {@code RECEIVED} run, and any older active
 *       run for the same PR is superseded (canceled) so only the latest head is in flight.</li>
 * </ul>
 * Run creation and the scheduling outbox event commit together; the HTTP caller acknowledges with
 * {@code 202} as soon as this returns — repository download and Agent execution happen later.
 */
@Service
public class WebhookAgentRunService {

    /** Outcome of {@link #startFromEvent}: the run plus whether it was newly created. */
    public record StartResult(AgentRun run, boolean created) {
    }

    private final AgentRunRepository agentRunRepository;
    private final AgentOutboxPublisher outboxPublisher;
    private final AgentStateMachine stateMachine;

    public WebhookAgentRunService(AgentRunRepository agentRunRepository,
                                  AgentOutboxPublisher outboxPublisher,
                                  AgentStateMachine stateMachine) {
        this.agentRunRepository = agentRunRepository;
        this.outboxPublisher = outboxPublisher;
        this.stateMachine = stateMachine;
    }

    @Transactional
    public StartResult startFromEvent(NormalizedPullRequestEvent event, ScmInstallation installation) {
        if (installation.getProjectId() == null || installation.getRepositoryId() == null) {
            throw new IllegalStateException("Installation is not bound to a project/repository");
        }
        String triggerKey = triggerKey(event);
        Optional<AgentRun> existing = agentRunRepository.findByTriggerKey(triggerKey);
        if (existing.isPresent()) {
            // Exact replay: no new run, no second publish.
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

        supersedeOlderRuns(event, run.getId());

        // Transactional outbox: the run and its scheduling event commit together.
        outboxPublisher.publishStepEvent(run.getId(), "AGENT_RUN_RECEIVED", triggerKey);
        return new StartResult(run, true);
    }

    /** Cancels any non-terminal run for the same PR other than the just-created one. */
    private void supersedeOlderRuns(NormalizedPullRequestEvent event, Long newRunId) {
        Instant now = Instant.now();
        for (AgentRun sibling : agentRunRepository.findByTriggerKeyStartingWith(prKey(event))) {
            if (sibling.getId().equals(newRunId) || sibling.getStatus().isTerminal()) {
                continue;
            }
            stateMachine.validateTransition(sibling.getStatus(), AgentRunStatus.CANCELED);
            sibling.setStatus(AgentRunStatus.CANCELED);
            sibling.setCancellationRequested(true);
            sibling.setFailureType("SUPERSEDED");
            sibling.setFailureMessage("Superseded by newer head " + event.headSha());
            sibling.setCompletedAt(now);
            agentRunRepository.save(sibling);
        }
    }

    /** Deterministic, idempotent run key for a normalized event (includes head SHA). */
    public static String triggerKey(NormalizedPullRequestEvent event) {
        return prKey(event) + event.headSha();
    }

    /** Prefix shared by every run for one PR, independent of head SHA. */
    private static String prKey(NormalizedPullRequestEvent event) {
        return event.provider().name().toLowerCase()
                + ":" + event.installationRef()
                + ":pr:" + event.pullRequestNumber()
                + ":";
    }
}
