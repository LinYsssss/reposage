package com.example.reposage.sandbox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the trusted sandbox runner.
 *
 * <p>The runner is a queue consumer with no inbound HTTP port (see {@code web-application-type:
 * none}). It pulls signed {@link SandboxJob}s from a dedicated RabbitMQ queue, verifies them, and
 * runs the requested command in a constrained ephemeral container. It is the only component allowed
 * to reach the Docker socket (via a restricted proxy in Compose); analyzed repository containers
 * never receive it.
 */
@SpringBootApplication
public class SandboxRunnerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SandboxRunnerApplication.class, args);
    }
}
