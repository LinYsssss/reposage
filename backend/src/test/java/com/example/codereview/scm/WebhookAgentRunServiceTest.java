package com.example.codereview.scm;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.codereview.agent.outbox.AgentOutboxPublisher;
import com.example.codereview.agent.outbox.AgentOutboxRepository;
import com.example.codereview.agent.run.AgentRun;
import com.example.codereview.agent.run.AgentRunRepository;
import com.example.codereview.agent.run.AgentRunStatus;
import com.example.codereview.agent.run.AgentStateMachine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

/**
 * Verifies the SCM-event → Agent Run rules: one verified event creates exactly one RECEIVED run and
 * one outbox publish, an exact replay returns the original run with no second publish, and a newer
 * head SHA starts a fresh run while superseding the older active run for the same PR.
 */
@DataJpaTest
@Import({WebhookAgentRunService.class, AgentOutboxPublisher.class, AgentStateMachine.class})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:webhookrun;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class WebhookAgentRunServiceTest {

    @Autowired
    private WebhookAgentRunService service;

    @Autowired
    private AgentRunRepository agentRuns;

    @Autowired
    private AgentOutboxRepository outbox;

    @Test
    void oneEventCreatesOneReceivedRunAndOnePublish() {
        WebhookAgentRunService.StartResult result = service.startFromEvent(event("headA", "d1"), installation());

        assertThat(result.created()).isTrue();
        assertThat(result.run().getStatus()).isEqualTo(AgentRunStatus.RECEIVED);
        assertThat(agentRuns.count()).isEqualTo(1);
        assertThat(outbox.count()).isEqualTo(1);
    }

    @Test
    void duplicateEventReturnsOriginalRunWithoutSecondPublish() {
        WebhookAgentRunService.StartResult first = service.startFromEvent(event("headA", "d1"), installation());
        // Same head SHA => same trigger key, even with a different delivery id.
        WebhookAgentRunService.StartResult second = service.startFromEvent(event("headA", "d2"), installation());

        assertThat(second.created()).isFalse();
        assertThat(second.run().getId()).isEqualTo(first.run().getId());
        assertThat(agentRuns.count()).isEqualTo(1);
        assertThat(outbox.count()).isEqualTo(1);
    }

    @Test
    void newerHeadShaCreatesNewRunAndSupersedesOlder() {
        WebhookAgentRunService.StartResult older = service.startFromEvent(event("headA", "d1"), installation());
        WebhookAgentRunService.StartResult newer = service.startFromEvent(event("headB", "d2"), installation());

        assertThat(newer.created()).isTrue();
        assertThat(agentRuns.count()).isEqualTo(2);

        AgentRun reloadedOlder = agentRuns.findById(older.run().getId()).orElseThrow();
        assertThat(reloadedOlder.getStatus()).isEqualTo(AgentRunStatus.CANCELED);
        assertThat(reloadedOlder.getCancellationRequested()).isTrue();
        assertThat(reloadedOlder.getFailureType()).isEqualTo("SUPERSEDED");

        AgentRun reloadedNewer = agentRuns.findById(newer.run().getId()).orElseThrow();
        assertThat(reloadedNewer.getStatus()).isEqualTo(AgentRunStatus.RECEIVED);
        assertThat(outbox.count()).isEqualTo(2);
    }

    private static NormalizedPullRequestEvent event(String headSha, String deliveryId) {
        return new NormalizedPullRequestEvent(
                ScmProviderType.GITHUB, "inst-1", "acme/widgets", "https://host/acme/widgets.git",
                7, "Add widget", "octocat", "feature/widget", "main",
                "base000", headSha, "opened", deliveryId);
    }

    private static ScmInstallation installation() {
        ScmInstallation installation = new ScmInstallation();
        installation.setProvider(ScmProviderType.GITHUB);
        installation.setExternalInstallationId("inst-1");
        installation.setProjectId(10L);
        installation.setRepositoryId(20L);
        return installation;
    }
}
