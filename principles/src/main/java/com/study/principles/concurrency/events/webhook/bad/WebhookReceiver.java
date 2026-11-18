package com.study.principles.concurrency.events.webhook.bad;

import com.study.principles.concurrency.events.webhook.Webhook;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * A typical first webhook receiver in a test framework. It works for one test on one laptop:
 * <ul>
 *   <li><b>Fixed port</b> ({@code webhook.port=8085} in the config). Two parallel tests, two CI jobs on one agent,
 *       or a receiver a crashed test did not stop: {@code BindException: Address already in use}.</li>
 *   <li><b>The test's code runs in the handler, before the answer.</b> The provider waits for 2xx with a timeout;
 *       a slow listener (parsing, a DB check, a breakpoint) makes the provider give up and <b>retry</b>, so the test
 *       sees the same event several times.</li>
 *   <li><b>Assertions in the handler vanish.</b> An {@code AssertionError} there is caught by the HTTP server, which
 *       drops the connection; the provider logs "io-error" and retries, and the test sees a missing webhook, not
 *       the failed assertion.</li>
 *   <li><b>No executor.</b> Every request runs on the server's single dispatcher thread: one slow request delays
 *       all the others, including the retries it caused.</li>
 * </ul>
 */
public final class WebhookReceiver implements AutoCloseable {

    public static final int PORT = 8085;

    private final HttpServer server;
    private final int port;

    public WebhookReceiver(Consumer<Webhook> listener) {
        this(PORT, listener);
    }

    public WebhookReceiver(int port, Consumer<Webhook> listener) {
        this.port = port;
        try {
            this.server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot listen on " + port, e);
        }
        server.createContext("/webhooks", exchange -> handle(exchange, listener));
        server.start();
    }

    public URI url() {
        return URI.create("http://localhost:" + port + "/webhooks");
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private static void handle(HttpExchange exchange, Consumer<Webhook> listener) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        var headers = exchange.getRequestHeaders();
        listener.accept(new Webhook(headers.getFirst("Webhook-Id"),
                Integer.parseInt(headers.getFirst("Webhook-Attempt")),
                body, headers.getFirst("Webhook-Signature"), Instant.now()));   // the test's code, then the answer
        exchange.sendResponseHeaders(200, -1);
        exchange.close();
    }
}
