package com.example.reposage.sandbox;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * The runner context starts as a non-web queue consumer: the signer, replay guard, placeholder
 * executor, and job consumer all wire up without a broker (listeners disabled).
 */
@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "sandbox.signing-secret=test-signing-secret"
})
class SandboxRunnerApplicationTests {

    @Autowired
    private SandboxJobConsumer consumer;

    @Autowired
    private SandboxExecutor executor;

    @Test
    void contextLoadsWithConsumerAndExecutor() {
        org.assertj.core.api.Assertions.assertThat(consumer).isNotNull();
        org.assertj.core.api.Assertions.assertThat(executor).isNotNull();
    }
}
