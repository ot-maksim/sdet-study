package com.study.principles.concurrency.events.kafka.good;

import com.study.principles.concurrency.Await;
import com.study.principles.concurrency.ReportFile;
import com.study.principles.concurrency.events.EventWaiter;
import com.study.principles.concurrency.events.SwitchableListener;
import com.study.principles.concurrency.events.kafka.Event;
import com.study.principles.concurrency.events.kafka.FakeBroker;
import com.study.principles.concurrency.events.kafka.FakeOrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The scenarios of {@code bad.BadKafkaEventsTest}, done right, plus the guarantees of {@link EventClient}. */
class GoodKafkaEventsTest {

    private static final Duration WAIT = Duration.ofSeconds(2);
    private static final Duration SHORT = Duration.ofMillis(300);

    private final FakeBroker broker = new FakeBroker().createTopics("orders", "payments");
    private final EventClient client = new EventClient(broker);
    private final List<AutoCloseable> toClose = new ArrayList<>(List.of(client));

    @AfterEach
    void closeAll() throws Exception {
        for (AutoCloseable c : toClose) {
            c.close();
        }
    }

    private <T extends AutoCloseable> T closeLater(T closeable) {
        toClose.add(closeable);
        return closeable;
    }

    // --- 1. Collecting from a foreign thread ----------------------------------------------------------

    @Test
    void blockingQueue_fedByTwoPollThreads_keepsEveryEvent() {
        int perTopic = 20_000;
        var payments = closeLater(new EventClient(broker));
        BlockingQueue<Event> received = new LinkedBlockingQueue<>();
        client.subscribe("orders", received::add);
        payments.subscribe("payments", received::add);

        for (int i = 0; i < perTopic; i++) {
            broker.publish("orders", "o" + i, "{}");
            broker.publish("payments", "p" + i, "{}");
        }

        Await.count(received::size, 2 * perTopic, Duration.ofSeconds(10));
        assertThat(received).hasSize(2 * perTopic);
    }

    // --- 2. Waiting: as long as needed, no longer ------------------------------------------------------

    @Test
    void pollMatching_skipsNoise_andReturnsAsSoonAsTheEventArrives() throws InterruptedException {
        var orders = closeLater(new FakeOrderService(broker, Duration.ofMillis(200)));
        BlockingQueue<Event> received = new LinkedBlockingQueue<>();

        try (var sub = new EventSubscription(client, "orders", received::add)) {
            broker.publish("orders", "order-of-a-parallel-test", "{}");
            long start = System.nanoTime();
            String myOrder = orders.createOrder("alice");

            Optional<Event> event = EventWaiter.pollMatching(received, e -> myOrder.equals(e.key()), Duration.ofSeconds(10));

            assertThat(event).as("OrderCreated for %s", myOrder).isPresent();
            assertThat(event.get().header("type")).isEqualTo("OrderCreated");
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
        }
    }

    @Test
    void pollMatching_returnsEmptyAfterTheTimeout_soTheAssertionCanSayWhat() throws InterruptedException {
        BlockingQueue<Event> received = new LinkedBlockingQueue<>();
        try (var sub = new EventSubscription(client, "orders", received::add)) {
            broker.publish("orders", "someone-else", "{}");

            assertThat(EventWaiter.pollMatching(received, e -> e.key().equals("never"), SHORT)).isEmpty();
        }
    }

    // --- 3. Subscribe: ready on return, only what comes after --------------------------------------------

    @Test
    void eventPublishedRightAfterSubscribeReturns_isDelivered() throws InterruptedException {
        BlockingQueue<Event> received = new LinkedBlockingQueue<>();
        try (var sub = new EventSubscription(client, "orders", received::add)) {
            broker.publish("orders", "order-1", "{}");

            assertThat(received.poll(WAIT.toMillis(), TimeUnit.MILLISECONDS)).extracting(Event::key).isEqualTo("order-1");
        }
    }

    @Test
    void eventsPublishedBeforeSubscribe_areNotDelivered() throws InterruptedException {
        broker.publish("orders", "yesterdays-order", "{}");
        BlockingQueue<Event> received = new LinkedBlockingQueue<>();
        try (var sub = new EventSubscription(client, "orders", received::add)) {
            broker.publish("orders", "order-1", "{}");

            assertThat(received.poll(WAIT.toMillis(), TimeUnit.MILLISECONDS)).extracting(Event::key).isEqualTo("order-1");
            assertThat(received.poll(SHORT.toMillis(), TimeUnit.MILLISECONDS)).isNull();
        }
    }

    @Test
    void subscribingToASecondTopic_doesNotMoveTheFirst() throws InterruptedException {
        BlockingQueue<Event> received = new LinkedBlockingQueue<>();
        try (var orders = new EventSubscription(client, "orders", received::add)) {
            broker.publish("orders", "order-1", "{}");
            try (var payments = new EventSubscription(client, "payments", received::add)) {
                broker.publish("orders", "order-2", "{}");
                broker.publish("payments", "payment-1", "{}");

                Await.count(received::size, 3, WAIT);
                assertThat(received).filteredOn(e -> e.topic().equals("orders"))
                        .extracting(Event::key).containsExactly("order-1", "order-2");   // not lost, not re-read
                assertThat(received).extracting(Event::key).contains("payment-1");
            }
        }
    }

    @Test
    void subscribeToUnknownTopic_failsWithTheReason() {
        assertThatThrownBy(() -> client.subscribe("ordrs", e -> { }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ordrs")
                .hasRootCauseMessage("Unknown topic: ordrs");
        assertThat(client.listenerCount("ordrs")).isZero();
    }

    @Test
    void subscribeOnAClosedClient_failsInsteadOfWaitingForever() {
        client.close();

        assertThatThrownBy(() -> client.subscribe("orders", e -> { }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Event client is closed");
    }

    // --- 4. Unsubscribe ---------------------------------------------------------------------------------

    @Test
    void eventSubscription_unsubscribesTheInstanceItSubscribed() {
        BlockingQueue<Event> received = new LinkedBlockingQueue<>();
        try (var sub = new EventSubscription(client, "orders", received::add)) {
            assertThat(client.listenerCount("orders")).isEqualTo(1);
        }
        assertThat(client.listenerCount("orders")).isZero();
    }

    @Test
    void eventSubscriptionClose_waitsOutTheDispatchInProgress_soReleasingIsSafe() {
        ReportFile report = new ReportFile();
        CountDownLatch dispatching = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch dispatchDone = new CountDownLatch(1);
        client.subscribe("orders", e -> {
            dispatching.countDown();
            Await.latch(release);
        });
        var sub = new EventSubscription(client, "orders", e -> report.write(e.key()));
        client.subscribe("orders", e -> dispatchDone.countDown());

        broker.publish("orders", "order-1", "{}");
        Await.latch(dispatching);

        sub.close();        // deactivate + unsubscribe
        report.close();
        release.countDown();
        Await.latch(dispatchDone);

        assertThat(report.writesAfterClose()).isZero();
    }

    /** synchronized: deactivate() does not return until the running delegate is done. */
    @Test
    void deactivate_waitsForTheRunningDelegate() throws Exception {
        ReportFile report = new ReportFile();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var listener = new SwitchableListener<String>(e -> {
            entered.countDown();
            Await.latch(release);
            report.write(e);
        });
        Thread pollThread = new Thread(() -> listener.accept("order-1"));
        pollThread.start();
        Await.latch(entered);

        CompletableFuture<Void> deactivated = CompletableFuture.runAsync(listener::deactivate);
        assertThatThrownBy(() -> deactivated.get(SHORT.toMillis(), TimeUnit.MILLISECONDS))
                .isInstanceOf(TimeoutException.class);   // blocked while the delegate runs

        release.countDown();
        deactivated.get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        report.close();
        pollThread.join();
        listener.accept("order-2");   // switched off: never reaches the delegate

        assertThat(report.lines()).containsExactly("order-1");
        assertThat(report.writesAfterClose()).isZero();
    }

    @Test
    void listenerUnsubscribingItselfDuringDispatch_isSafe() {
        BlockingQueue<Event> received = new LinkedBlockingQueue<>();
        client.subscribe("orders", received::add);
        client.subscribe("orders", new Consumer<>() {
            @Override
            public void accept(Event e) {
                client.unsubscribe("orders", this);
            }
        });

        broker.publish("orders", "order-1", "{}");
        broker.publish("orders", "order-2", "{}");

        Await.count(received::size, 2, WAIT);
        assertThat(client.listenerCount("orders")).isEqualTo(1);
    }

    // --- 5. One failing listener ------------------------------------------------------------------------

    @Test
    void oneFailingListener_doesNotSilenceTheOthers() throws InterruptedException {
        BlockingQueue<Event> received = new LinkedBlockingQueue<>();
        client.subscribe("orders", e -> parseOrder(e.value()));
        try (var sub = new EventSubscription(client, "orders", received::add)) {
            broker.publish("orders", "garbage", "not json");
            broker.publish("orders", "order-1", "{\"orderId\":\"order-1\"}");

            assertThat(EventWaiter.pollMatching(received, e -> e.key().equals("order-1"), WAIT)).isPresent();
        }
    }

    static void parseOrder(String json) {
        if (!json.startsWith("{")) {
            throw new IllegalArgumentException("Not JSON: " + json);
        }
    }
}
