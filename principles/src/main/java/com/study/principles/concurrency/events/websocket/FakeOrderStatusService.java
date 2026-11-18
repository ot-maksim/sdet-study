package com.study.principles.concurrency.events.websocket;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * The SUT: {@code pay} answers at once (like {@code POST /orders/{id}/pay} returning 202) and pushes an
 * {@code OrderStatus} message to the {@code orders} channel <b>later</b>, from its own thread.
 *
 * <p>The state is kept regardless of who listened: {@link #status} is the "{@code GET /orders/{id}}" a test
 * can fall back to when a push was lost in a reconnect gap.
 */
public final class FakeOrderStatusService implements AutoCloseable {

    public static final String CHANNEL = "orders";

    private final FakeWsServer server;
    private final Supplier<Duration> pushLatency;
    private final Map<String, String> statuses = new ConcurrentHashMap<>();
    private final AtomicLong ids = new AtomicLong();
    private final AtomicInteger pushesSent = new AtomicInteger();
    private final ScheduledExecutorService pusher = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "order-status-pusher");
        t.setDaemon(true);
        return t;
    });

    public FakeOrderStatusService(FakeWsServer server, Duration pushLatency) {
        this(server, () -> pushLatency);
    }

    public FakeOrderStatusService(FakeWsServer server, Supplier<Duration> pushLatency) {
        this.server = server;
        this.pushLatency = pushLatency;
    }

    public String createOrder() {
        String orderId = "order-" + ids.incrementAndGet();
        statuses.put(orderId, "NEW");
        return orderId;
    }

    /** The state changes now; the push follows after the latency. */
    public void pay(String orderId) {
        statuses.put(orderId, "PAID");
        pusher.schedule(() -> {
            server.publish(CHANNEL, Messages.orderStatus(orderId, "PAID"));
            pushesSent.incrementAndGet();
        }, pushLatency.get().toNanos(), TimeUnit.NANOSECONDS);
    }

    /** "GET /orders/{id}": the current state, whatever happened to the pushes. */
    public String status(String orderId) {
        return statuses.get(orderId);
    }

    /** How many pushes were handed to the server (not: delivered to anybody). */
    public int pushesSent() {
        return pushesSent.get();
    }

    @Override
    public void close() {
        pusher.shutdownNow();
    }
}
