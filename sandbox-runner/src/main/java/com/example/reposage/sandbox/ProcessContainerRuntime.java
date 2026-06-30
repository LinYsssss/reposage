package com.example.reposage.sandbox;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Default {@link ContainerRuntime} that shells out to the Docker CLI via {@link ProcessBuilder}.
 *
 * <p>Combined stdout/stderr is drained on a daemon thread (so a full pipe can't deadlock the wait)
 * and bounded to {@link #MAX_OUTPUT} characters with a truncation flag. A run that exceeds its
 * timeout is force-killed and reported as {@link ContainerRuntime#TIMED_OUT}.
 */
@Component
public class ProcessContainerRuntime implements ContainerRuntime {

    private static final int MAX_OUTPUT = 8000;

    @Override
    public RunOutcome run(List<String> args, long timeoutMs) {
        Process process;
        try {
            process = new ProcessBuilder(args).redirectErrorStream(true).start();
        } catch (Exception ex) {
            return new RunOutcome(1, "failed to start: " + ex.getMessage(), false);
        }

        StringBuilder output = new StringBuilder();
        boolean[] truncated = {false};
        Thread reader = new Thread(() -> drain(process, output, truncated));
        reader.setDaemon(true);
        reader.start();

        try {
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
                return new RunOutcome(TIMED_OUT, output.toString(), truncated[0]);
            }
            reader.join(1000);
            return new RunOutcome(process.exitValue(), output.toString(), truncated[0]);
        } catch (InterruptedException ex) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            return new RunOutcome(TIMED_OUT, output.toString(), truncated[0]);
        }
    }

    @Override
    public void execQuietly(List<String> args) {
        try {
            Process process = new ProcessBuilder(args).redirectErrorStream(true).start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ignored) {
            // Best-effort cleanup: a missing container or engine hiccup must not propagate.
        }
    }

    private static void drain(Process process, StringBuilder output, boolean[] truncated) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            int ch;
            while ((ch = reader.read()) != -1) {
                if (output.length() < MAX_OUTPUT) {
                    output.append((char) ch);
                } else {
                    truncated[0] = true;
                }
            }
        } catch (Exception ignored) {
            // Stream closed/!readable: keep whatever was captured.
        }
    }
}
