package com.study.principles.concurrency.events.webhook.good;

import com.study.principles.concurrency.events.EventWaiter;
import com.study.principles.concurrency.events.webhook.Webhook;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * The test as a webhook server: real HTTP on the loopback interface, a free port, and nothing but
 * "record and answer" in the handler.
 *
 * <pre>{@code
 * try (var receiver = WebhookReceiver.start()) {               // listening on its own port
 *     provider.registerWebhook(receiver.url(), SECRET);        // ready: the SUT knows where to call
 *     String orderId = ...;
 *     provider.charge(orderId);
 *     Webhook w = receiver.await(hook -> orderId.equals(hook.field("orderId")), Duration.ofSeconds(5)).orElseThrow();
 *     assertThat(w.hasValidSignature(SECRET)).isTrue();         // asserted in the test thread
 * }
 * }</pre>
 *
 * Design:
 * <ul>
 *   <li><b>Port 0.</b> The OS picks a free port; {@link #url()} reads it back. No clashes between parallel tests,
 *       CI jobs or a leftover receiver.</li>
 *   <li><b>Answer first, think later.</b> The handler records the delivery and answers at once: the provider sees
 *       success within its timeout, and does not retry because of the test's slowness. All checks happen in the test
 *       thread, where an assertion fails the test.</li>
 *   <li><b>Own executor.</b> Requests are handled on {@code webhook-receiver} threads, not on the dispatcher:
 *       concurrent deliveries do not queue behind each other.</li>
 *   <li><b>All attempts are kept.</b> {@link #received()} is everything that arrived, retries included;
 *       {@link #distinctEvents()} is one per event id. The test chooses which question it asks.</li>
 *   <li><b>Scriptable answers.</b> {@link #respondNext} makes the next deliveries fail, to test the SUT's retry policy.</li>
 *   <li>The handler never throws: an internal failure is logged and answered with 500, visibly.</li>
 * </ul>
 */
public final class WebhookReceiver implements AutoCloseable {

    private static final Logger log = System.getLogger(WebhookReceiver.class.getName());
    private static final String PATH = "/webhooks";
    private static final AtomicInteger threadIds = new AtomicInteger();

    private final HttpServer server;
    private final ExecutorService handlers;
    private final BlockingQueue<Webhook> inbox = new LinkedBlockingQueue<>();
    private final List<Webhook> received = new CopyOnWriteArrayList<>();
    private final Queue<Integer> scriptedStatuses = new ConcurrentLinkedQueue<>();

    private WebhookReceiver(HttpServer server, ExecutorService handlers) {
        this.server = server;
        this.handlers = handlers;
    }

    /** Binds to a free port on the loopback interface and starts listening. */
    public static WebhookReceiver start() {
        HttpServer server;
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot start the webhook receiver", e);
        }
        ExecutorService handlers = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "webhook-receiver-" + threadIds.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        WebhookReceiver receiver = new WebhookReceiver(server, handlers);
        server.createContext(PATH, receiver::handle);
        server.setExecutor(handlers);
        server.start();
        return receiver;
    }

    /** The URL to register in the SUT, with the port the OS gave us. */
    public URI url() {
        InetSocketAddress address = server.getAddress();
        return URI.create("http://" + address.getAddress().getHostAddress() + ":" + address.getPort() + PATH);
    }

    /** The next {@code times} deliveries get {@code status} instead of 204, e.g. {@code respondNext(503, 2)}. */
    public void respondNext(int status, int times) {
        for (int i = 0; i < times; i++) {
            scriptedStatuses.add(status);
        }
    }

    /**
     * Waits for the first matching delivery, skipping the others; empty after the timeout. Retries of an already
     * returned event stay in the inbox and match again: use {@link #distinctEvents()} to count events.
     */
    public Optional<Webhook> await(Predicate<Webhook> matcher, Duration timeout) throws InterruptedException {
        return EventWaiter.pollMatching(inbox, matcher, timeout);
    }

    /** Every delivery, in arrival order, retries included. */
    public List<Webhook> received() {
        return List.copyOf(received);
    }

    /** One delivery per event id: the first one that arrived. */
    public List<Webhook> distinctEvents() {
        Map<String, Webhook> byId = new LinkedHashMap<>();
        for (Webhook w : received) {
            byId.putIfAbsent(w.eventId(), w);
        }
        return List.copyOf(byId.values());
    }

    @Override
    public void close() {
        server.stop(0);
        handlers.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        int status = 204;
        try {
            if (!"POST".equals(exchange.getRequestMethod())) {
                status = 405;
            } else {
                Webhook webhook = parse(exchange.getRequestHeaders(), exchange.getRequestBody().readAllBytes());
                received.add(webhook);
                inbox.add(webhook);
                Integer scripted = scriptedStatuses.poll();
                if (scripted != null) {
                    status = scripted;
                }
            }
        } catch (RuntimeException | IOException e) {
            log.log(Logger.Level.WARNING, "Webhook receiver failed on a request: {0}", e.toString());
            status = 500;
        }
        try {
            exchange.sendResponseHeaders(status, -1);
        } finally {
            exchange.close();
        }
    }

    private static Webhook parse(Headers headers, byte[] body) {
        String attempt = headers.getFirst("Webhook-Attempt");
        return new Webhook(headers.getFirst("Webhook-Id"),
                attempt == null ? 0 : Integer.parseInt(attempt),
                new String(body, StandardCharsets.UTF_8),
                headers.getFirst("Webhook-Signature"),
                Instant.now());
    }
}
