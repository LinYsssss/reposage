package com.example.codereview.scm;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Minimal in-JVM HTTP server for testing provider publishers: it records every request (method,
 * path, headers, body) and replies with a configurable status. Dependency-free (JDK
 * {@code com.sun.net.httpserver}) so it works without WireMock.
 */
public class RecordingHttpServer implements AutoCloseable {

    public record Recorded(String method, String path, String body, Map<String, String> headers) {
    }

    private final HttpServer server;
    private final List<Recorded> requests = new CopyOnWriteArrayList<>();
    private volatile int responseStatus = 201;

    public RecordingHttpServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
            exchange.getRequestHeaders().forEach((k, v) -> headers.put(k, String.join(",", v)));
            requests.add(new Recorded(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    new String(body, StandardCharsets.UTF_8),
                    headers));
            exchange.sendResponseHeaders(responseStatus, -1);
            exchange.close();
        });
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public List<Recorded> requests() {
        return requests;
    }

    public void setResponseStatus(int status) {
        this.responseStatus = status;
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
