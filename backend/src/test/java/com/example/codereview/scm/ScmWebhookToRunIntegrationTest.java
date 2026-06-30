package com.example.codereview.scm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.codereview.agent.outbox.AgentOutboxRepository;
import com.example.codereview.agent.run.AgentRun;
import com.example.codereview.agent.run.AgentRunRepository;
import com.example.codereview.agent.run.AgentRunStatus;
import com.example.codereview.common.security.CryptoService;
import com.example.codereview.support.IntegrationTestContainers;
import com.example.codereview.webhook.WebhookSignatures;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * End-to-end webhook → Agent Run path against real PostgreSQL (with the V6 SCM migration applied via
 * Flyway) and RabbitMQ. A signed GitHub delivery for a bound installation creates a RECEIVED run, a
 * PROCESSED delivery, and a transactional outbox event. Skipped automatically without Docker.
 */
@ActiveProfiles("prod")
@SpringBootTest(properties = {
        "app.review.inline=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "app.security.token-secret=test-secret-at-least-32-characters",
        "app.security.token-encrypt-key=test-encryption-key-at-least-32"
})
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ScmWebhookToRunIntegrationTest extends IntegrationTestContainers {

    private static final String SECRET = "gh-webhook-secret";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        register(registry);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScmInstallationRepository installations;

    @Autowired
    private WebhookDeliveryRepository deliveries;

    @Autowired
    private AgentRunRepository agentRuns;

    @Autowired
    private AgentOutboxRepository outbox;

    @Autowired
    private CryptoService crypto;

    @Test
    void signedGithubDeliveryCreatesRunDeliveryAndOutboxEvent() throws Exception {
        ScmInstallation installation = new ScmInstallation();
        installation.setProvider(ScmProviderType.GITHUB);
        installation.setExternalInstallationId("42");
        installation.setProjectId(1L);
        installation.setRepositoryId(2L);
        installation.setEncryptedWebhookSecret(crypto.encrypt(SECRET));
        installation.setActive(true);
        installations.save(installation);

        byte[] body = fixture("/webhooks/github/opened.json");
        String signature = "sha256=" + WebhookSignatures.hmacSha256Hex(body, SECRET);

        mockMvc.perform(post("/api/webhooks/scm/github")
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-GitHub-Delivery", "it-delivery-1")
                        .header("X-Hub-Signature-256", signature)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.outcome").value("PROCESSED"));

        AgentRun run = agentRuns.findByTriggerKey("github:42:pr:7:headsha111").orElseThrow();
        assertThat(run.getStatus()).isEqualTo(AgentRunStatus.RECEIVED);
        assertThat(deliveries.findByProviderAndDeliveryId(ScmProviderType.GITHUB, "it-delivery-1"))
                .get().extracting(WebhookDelivery::getStatus).isEqualTo(WebhookDeliveryStatus.PROCESSED);
        assertThat(outbox.findAll()).anyMatch(e -> e.getAgentRunId().equals(run.getId()));
    }

    private static byte[] fixture(String path) {
        try (var in = ScmWebhookToRunIntegrationTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("missing fixture " + path);
            }
            return in.readAllBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
