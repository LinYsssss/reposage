package com.example.codereview.webhook;

import com.example.codereview.common.api.ApiResponse;
import com.example.codereview.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Inbound GitHub webhook endpoint. Public (no bearer token) but gated by HMAC signature
 * verification — the raw body is read as bytes precisely so the signature can be checked before
 * parsing. On {@code pull_request} events it auto-triggers a review.
 */
@RestController
@RequestMapping("/api/webhooks")
public class GithubWebhookController {

    private final GithubWebhookService service;
    private final boolean enabled;
    private final String secret;

    public GithubWebhookController(
            GithubWebhookService service,
            @Value("${app.webhook.enabled:true}") boolean enabled,
            @Value("${app.webhook.github.secret:}") String secret) {
        this.service = service;
        this.enabled = enabled;
        this.secret = secret;
    }

    @PostMapping(value = "/github", consumes = MediaType.ALL_VALUE)
    public ApiResponse<String> github(
            @RequestBody(required = false) byte[] body,
            @RequestHeader(value = "X-GitHub-Event", required = false) String event,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String signature) {
        if (!enabled) {
            return ApiResponse.ok("webhook disabled");
        }
        byte[] raw = body == null ? new byte[0] : body;
        if (!WebhookSignatures.verifyGithub(raw, signature, secret)) {
            throw new BusinessException(401, "Webhook 签名校验失败");
        }
        return ApiResponse.ok(service.handle(event, new String(raw, StandardCharsets.UTF_8)));
    }
}
