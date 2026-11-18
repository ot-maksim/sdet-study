package com.study.principles.concurrency.events.kafka;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * The SUT: {@code createOrder} answers at once (like {@code POST /orders} returning 201) and publishes
 * {@code OrderCreated} to the {@code orders} topic <b>later</b>, from its own thread (an outbox relay).
 * The latency is a supplier, so a demo can make it vary from order to order.
 *
 * <p>Other tests and other services create orders in the same topic: from a test's point of view, most of
 * what arrives there is noise.
 */
public final class FakeOrderService implements AutoCloseable {

    public static final String TOPIC = "orders";

    private final FakeBroker broker;
    private final Supplier<Duration> latency;
    private final AtomicLong ids = new AtomicLong();
    private final ScheduledExecutorService relay = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "order-outbox-relay");
        t.setDaemon(true);
        return t;
    });

    public FakeOrderService(FakeBroker broker, Duration latency) {
        this(broker, () -> latency);
    }

    public FakeOrderService(FakeBroker broker, Supplier<Duration> latency) {
        this.broker = broker.createTopics(TOPIC);
        this.latency = latency;
    }

    /** Returns the new order id; the event follows after the latency. */
    public String createOrder(String customer) {
        String orderId = "order-" + ids.incrementAndGet();
        String json = "{\"orderId\":\"" + orderId + "\",\"customer\":\"" + customer + "\"}";
        relay.schedule(
                () -> broker.publish(TOPIC, orderId, json, Map.of("type", "OrderCreated")),
                latency.get().toNanos(), TimeUnit.NANOSECONDS);
        return orderId;
    }

    @Override
    public void close() {
        relay.shutdownNow();
    }
}
