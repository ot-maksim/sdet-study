package com.study.principles.concurrency.events.websocket;

import com.study.principles.concurrency.events.EventWaiter;
import com.study.principles.concurrency.events.websocket.good.ConnectionLostException;
import com.study.principles.concurrency.events.websocket.good.WsEventClient;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Two checks, each written with the bad and the good client.
 * <ol>
 *   <li>"After pay, an OrderStatus push for my order arrives": the server takes 0–30 ms to register a
 *       subscription, the SUT pushes 0–20 ms after pay. 30 runs each.</li>
 *   <li>The server restarts 100 ms into the wait: how long until the test knows, and what does it say?</li>
 * </ol>
 *
 * <pre>
 * mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.concurrency.events.websocket.WebSocketEventsDemo
 * </pre>
 */
public final class WebSocketEventsDemo {

    private static final int RUNS = 30;
    private static final Duration WAIT = Duration.ofSeconds(3);

    public static void main(String[] args) throws Exception {
        try (var server = new FakeWsServer(() -> millis(0, 30), 64);
             var orders = new FakeOrderStatusService(server, () -> millis(0, 20))) {

            int ok = 0;
            for (int i = 0; i < RUNS; i++) {
                try (var client = new com.study.principles.concurrency.events.websocket.bad.WsEventClient(server)) {
                    BlockingQueue<String> received = new LinkedBlockingQueue<>();
                    client.subscribe(FakeOrderStatusService.CHANNEL, received::add);
                    String id = orders.createOrder();
                    orders.pay(id);
                    ok += EventWaiter.pollMatching(received, isOrder(id), Duration.ofMillis(300)).isPresent() ? 1 : 0;
                }
            }
            System.out.printf("%-44s %2d/%d ok%n", "bad: subscribe = frame sent, then pay", ok, RUNS);

            ok = 0;
            for (int i = 0; i < RUNS; i++) {
                try (var client = WsEventClient.connect(server, WAIT);
                     var sub = client.subscribe(FakeOrderStatusService.CHANNEL)) {
                    String id = orders.createOrder();
                    orders.pay(id);
                    ok += sub.await(isOrder(id), WAIT).isPresent() ? 1 : 0;
                }
            }
            System.out.printf("%-44s %2d/%d ok%n", "good: subscribe = server acked, then pay", ok, RUNS);

            restartWhileWaitingBad(server);
            restartWhileWaitingGood(server);
        }
    }

    private static void restartWhileWaitingBad(FakeWsServer server) throws Exception {
        try (var client = new com.study.principles.concurrency.events.websocket.bad.WsEventClient(server)) {
            BlockingQueue<String> received = new LinkedBlockingQueue<>();
            client.subscribe(FakeOrderStatusService.CHANNEL, received::add);
            Thread.sleep(100);   // the bad client needs a warm-up: it cannot tell when it is subscribed
            restartIn100ms(server);

            long start = System.nanoTime();
            Optional<String> event = EventWaiter.pollMatching(received, isOrder("order-x"), WAIT);
            System.out.printf("%-44s gave up after %4d ms: %s%n", "bad: server restarts while the test waits",
                    sinceMillis(start), event.isPresent() ? "found" : "\"no OrderStatus for order-x\"");
        }
    }

    private static void restartWhileWaitingGood(FakeWsServer server) throws Exception {
        try (var client = WsEventClient.connect(server, WAIT);
             var sub = client.subscribe(FakeOrderStatusService.CHANNEL)) {
            restartIn100ms(server);

            long start = System.nanoTime();
            try {
                sub.await(isOrder("order-x"), WAIT);
            } catch (ConnectionLostException e) {
                System.out.printf("%-44s failed after  %4d ms: %s%n", "good: server restarts while the test waits",
                        sinceMillis(start), e.getMessage());
            }
        }
    }

    private static void restartIn100ms(FakeWsServer server) {
        CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS)
                .execute(() -> server.dropAll(1001, "Going away"));
    }

    private static Predicate<String> isOrder(String orderId) {
        return m -> orderId.equals(Messages.field(m, "orderId"));
    }

    private static Duration millis(long from, long to) {
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(from, to));
    }

    private static long sinceMillis(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
