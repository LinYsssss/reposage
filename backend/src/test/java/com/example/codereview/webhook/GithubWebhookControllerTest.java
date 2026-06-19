package com.example.codereview.webhook;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "app.security.token-secret=test-secret",
        "app.security.token-encrypt-key=test-encrypt-key",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "management.health.rabbit.enabled=false",
        "app.webhook.github.secret=test-webhook-secret"
})
@AutoConfigureMockMvc
class GithubWebhookControllerTest {

    private static final String SECRET = "test-webhook-secret";
    private static final String PAYLOAD =
            "{\"action\":\"opened\",\"pull_request\":{\"number\":7,\"title\":\"t\",\"head\":{\"ref\":\"f\",\"sha\":\"a\"},"
            + "\"base\":{\"ref\":\"main\",\"sha\":\"b\"}},\"repository\":{\"full_name\":\"acme/unmapped-repo\"}}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsInvalidSignature() throws Exception {
        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-Hub-Signature-256", "sha256=deadbeef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYLOAD))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsValidSignatureAndRunsPipeline() throws Exception {
        byte[] body = PAYLOAD.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String signature = "sha256=" + WebhookSignatures.hmacSha256Hex(body, SECRET);
        mockMvc.perform(post("/api/webhooks/github")
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-Hub-Signature-256", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                // No repository bound to acme/unmapped-repo, so the pipeline no-ops safely.
                .andExpect(jsonPath("$.data").value(org.hamcrest.Matchers.containsString("no matching repository")));
    }
}
