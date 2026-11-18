package com.study.principles.concurrency.events.kafka;

import com.study.principles.concurrency.events.EventWaiter;
import com.study.principles.concurrency.events.kafka.good.EventClient;
import com.study.principles.concurrency.events.kafka.good.EventSubscription;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * One check, "after createOrder an OrderCreated event with my order id is published", written four ways
 * and run 30 times each. The SUT publishes after 20–300 ms; parallel tests create orders in the same topic.
 *
 * <pre>
 * mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.concurrency.events.kafka.KafkaEventsDemo
 * </pre>
 */
public final class KafkaEventsDemo {

    private static final int RUNS = 30;

    public static void main(String[] args) throws Exception {
        FakeBroker broker = new FakeBroker();
        try (var orders = new FakeOrderService(broker,
                () -> Duration.ofMillis(ThreadLocalRandom.current().nextLong(20, 300)));
             var client = new EventClient(broker)) {

            Thread noise = startParallelTests(orders);

            run("sleep(100), then look in the list", () -> {
                List<Event> received = new CopyOnWriteArrayList<>();
                try (var sub = new EventSubscription(client, FakeOrderService.TOPIC, received::add)) {
                    String id = orders.createOrder("alice");
                    Thread.sleep(100);
                    return received.stream().anyMatch(e -> id.equals(e.key()));
                }
            });
            run("sleep(400), then look in the list", () -> {
                List<Event> received = new CopyOnWriteArrayList<>();
                try (var sub = new EventSubscription(client, FakeOrderService.TOPIC, received::add)) {
                    String id = orders.createOrder("alice");
                    Thread.sleep(400);
                    return received.stream().anyMatch(e -> id.equals(e.key()));
                }
            });
            run("poll(5 s): the first event is mine", () -> {
                BlockingQueue<Event> received = new LinkedBlockingQueue<>();
                try (var sub = new EventSubscription(client, FakeOrderService.TOPIC, received::add)) {
                    String id = orders.createOrder("alice");
                    Event first = received.poll(5, TimeUnit.SECONDS);
                    return first != null && id.equals(first.key());
                }
            });
            run("EventWaiter.pollMatching(5 s)", () -> {
                BlockingQueue<Event> received = new LinkedBlockingQueue<>();
                try (var sub = new EventSubscription(client, FakeOrderService.TOPIC, received::add)) {
                    String id = orders.createOrder("alice");
                    Optional<Event> mine = EventWaiter.pollMatching(received, e -> id.equals(e.key()), Duration.ofSeconds(5));
                    return mine.isPresent();
                }
            });

            noise.interrupt();
        }
    }

    /** Other tests of the suite, creating their own orders every few milliseconds. */
    private static Thread startParallelTests(FakeOrderService orders) {
        Thread noise = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                orders.createOrder("someone-else");
                try {
                    Thread.sleep(15);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, "parallel-tests");
        noise.setDaemon(true);
        noise.start();
        return noise;
    }

    private interface Check {
        boolean passed() throws Exception;
    }

    private static void run(String name, Check check) throws Exception {
        int ok = 0;
        long totalNanos = 0;
        for (int i = 0; i < RUNS; i++) {
            long start = System.nanoTime();
            if (check.passed()) {
                ok++;
            }
            totalNanos += System.nanoTime() - start;
        }
        System.out.printf("%-38s %2d/%d ok, avg %4d ms per test%n",
                name, ok, RUNS, TimeUnit.NANOSECONDS.toMillis(totalNanos / RUNS));
    }
}
