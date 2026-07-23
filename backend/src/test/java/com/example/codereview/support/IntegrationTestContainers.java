package com.example.codereview.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;

public abstract class IntegrationTestContainers {

    private static final String POSTGRES_PASSWORD = "test-password-1234";
    private static final String RABBIT_PASSWORD = "rabbitmq-password-1234";

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withDatabaseName("code_review")
                    .withUsername("code_review")
                    .withPassword(POSTGRES_PASSWORD);

    @Container
    protected static final RabbitMQContainer RABBIT =
            new RabbitMQContainer("rabbitmq:3.13-management")
                    .withAdminPassword(RABBIT_PASSWORD);

    protected static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }
}
