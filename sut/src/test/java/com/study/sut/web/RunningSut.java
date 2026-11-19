package com.study.sut.web;

import com.study.sut.SutApp;
import com.study.sut.users.InMemoryUserRepository;
import com.study.sut.users.UserService;
import io.javalin.Javalin;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.stream.Collectors;

/** The SUT on a free port with in-memory storage and a fixed clock; a plain JDK client, redirects not followed. */
final class RunningSut implements AutoCloseable {

    static final Instant NOW = Instant.parse("2026-11-19T10:15:30.123456789Z");

    private final Javalin app;
    private final HttpClient http = HttpClient.newHttpClient();

    RunningSut() {
        app = SutApp.create(new UserService(new InMemoryUserRepository(), Clock.fixed(NOW, ZoneOffset.UTC))).start(0);
    }

    HttpResponse<String> get(String path) {
        return send(HttpRequest.newBuilder(uri(path)).GET());
    }

    HttpResponse<String> delete(String path) {
        return send(HttpRequest.newBuilder(uri(path)).DELETE());
    }

    HttpResponse<String> postJson(String path, String body) {
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    HttpResponse<String> postForm(String path, Map<String, String> fields) {
        String body = fields.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        return send(HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + app.port() + path);
    }

    private HttpResponse<String> send(HttpRequest.Builder request) {
        try {
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        app.stop();
    }
}
