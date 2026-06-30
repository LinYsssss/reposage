package com.example.reposage.sandbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ wiring for the runner: a single dedicated, durable job queue and JSON message conversion
 * for {@link SignedSandboxJob} envelopes. The runner binds only this queue; it neither exposes an
 * exchange nor any HTTP port.
 */
@Configuration
public class SandboxRunnerRabbitConfig {

    public static final String SANDBOX_JOB_QUEUE = "sandbox.job.queue";

    @Bean
    public Queue sandboxJobQueue() {
        return QueueBuilder.durable(SANDBOX_JOB_QUEUE).build();
    }

    @Bean
    public MessageConverter sandboxMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public ObjectMapper sandboxObjectMapper() {
        return new ObjectMapper();
    }
}
