package com.example.codereview.agent.run;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false"
})
class AgentRunPersistenceTest {

    @Autowired
    private AgentRunRepository agentRunRepository;

    @Autowired
    private AgentStepRepository agentStepRepository;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    void shouldPersistRunAndStepsWithOptimisticLocking() {
        // Create a RECEIVED run
        AgentRun run = new AgentRun();
        run.setTriggerKey("github:pr:123:sync");
        run.setProjectId(1L);
        run.setRepositoryId(2L);
        run.setHeadSha("abc123");
        run.setBaseSha("def456");
        run.setStatus(AgentRunStatus.RECEIVED);
        run.setStartedAt(Instant.now());
        run = agentRunRepository.save(run);

        assertThat(run.getId()).isNotNull();
        assertThat(run.getVersion()).isEqualTo(0);
        assertThat(run.getCreatedAt()).isNotNull();
        assertThat(run.getUpdatedAt()).isNotNull();

        // Append a step
        AgentStep step = new AgentStep();
        step.setAgentRunId(run.getId());
        step.setSequenceNo(1);
        step.setStepType("PREPARING_REPOSITORY");
        step.setStatus(AgentStepStatus.PENDING);
        step.setStartedAt(Instant.now());
        step = agentStepRepository.save(step);

        assertThat(step.getId()).isNotNull();
        assertThat(step.getAgentRunId()).isEqualTo(run.getId());

        // Reload both
        AgentRun reloadedRun = agentRunRepository.findById(run.getId()).orElseThrow();
        assertThat(reloadedRun.getTriggerKey()).isEqualTo("github:pr:123:sync");
        assertThat(reloadedRun.getStatus()).isEqualTo(AgentRunStatus.RECEIVED);

        var steps = agentStepRepository.findByAgentRunIdOrderBySequenceNoAsc(run.getId());
        assertThat(steps).hasSize(1);
        assertThat(steps.get(0).getSequenceNo()).isEqualTo(1);
        assertThat(steps.get(0).getStepType()).isEqualTo("PREPARING_REPOSITORY");

        // Verify @Version field is present and increments on update
        assertThat(run.getVersion()).isEqualTo(0);

        run.setStatus(AgentRunStatus.PLANNING);
        run = agentRunRepository.saveAndFlush(run);
        assertThat(run.getVersion()).isEqualTo(1);

        run.setStatus(AgentRunStatus.EXECUTING_TOOLS);
        run = agentRunRepository.saveAndFlush(run);
        assertThat(run.getVersion()).isEqualTo(2);
    }
}
