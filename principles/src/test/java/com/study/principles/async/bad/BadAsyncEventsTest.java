package com.study.principles.async.bad;

import com.study.principles.async.Await;
import com.study.principles.async.Event;
import com.study.principles.async.FakeBroker;
import com.study.principles.async.FakeOrderService;
import com.study.principles.async.ReportFile;
import com.study.principles.async.good.EventWaiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Characterization tests: they pass <i>because</i> they pin the bugs. Two kinds of smell:
 * test-side (how the test collects, waits, unsubscribes) on top of the good {@code EventClient},
 * and client-side, in {@link EventClient} and {@link VolatileSwitchableListener} of this package.
 */
class BadAsyncEventsTest {

    private static final Duration SHORT = Duration.ofMillis(500);

    private final FakeBroker broker = new FakeBroker().createTopics("orders", "payments");
    private final List<AutoCloseable> toClose = new ArrayList<>();

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

    // --- 1. The callback runs on somebody else's thread -----------------------------------------------

    /**
     * Two clients (e.g. orders and payments) feed one {@code ArrayList}. Lost updates are a race, so the
     * scenario repeats until it shows; on a multi-core machine it shows in the first round.
     */
    @Test
    void arrayList_fedByTwoPollThreads_losesEvents() {
        int perTopic = 20_000;
        int rounds = 0;
        boolean lost = false;
        while (!lost && rounds++ < 20) {
            var orders = closeLater(new com.study.principles.async.good.EventClient(broker));
            var payments = closeLater(new com.study.principles.async.good.EventClient(broker));
            List<Event> received = new ArrayList<>();
            AtomicInteger delivered = new AtomicInteger();
            AtomicInteger threw = new AtomicInteger();
            Consumer<Event> collect = e -> {
                delivered.incrementAndGet();
                try {
                    received.add(e);   // what the test wrote: received::add
                } catch (RuntimeException broken) {   // ArrayIndexOutOfBounds while growing
                    threw.incrementAndGet();
                }
            };
            orders.subscribe("orders", collect);
            payments.subscribe("payments", collect);

            for (int i = 0; i < perTopic; i++) {
                broker.publish("orders", "o" + i, "{}");
                broker.publish("payments", "p" + i, "{}");
            }
            Await.count(delivered::get, 2 * perTopic, Duration.ofSeconds(10));

            lost = received.size() < 2 * perTopic || threw.get() > 0;
        }
        assertThat(lost).as("ArrayList lost an event or threw within %d rounds", rounds).isTrue();
    }

    // --- 2. Waiting --------------------------------------------------------------------------------

    @Test
    void assertingRightAfterTheAction_seesNothingYet() {
        var client = closeLater(new com.study.principles.async.good.EventClient(broker));
        var orders = closeLater(new FakeOrderService(broker, Duration.ofMillis(300)));
        List<Event> received = new CopyOnWriteArrayList<>();
        client.subscribe("orders", received::add);

        orders.createOrder("alice");

        assertThat(received).isEmpty();   // the event is still in the outbox
    }

    @Test
    void fixedSleep_shorterThanThisRunsLatency_missesTheEvent() throws InterruptedException {
        var client = closeLater(new com.study.principles.async.good.EventClient(broker));
        var orders = closeLater(new FakeOrderService(broker, Duration.ofMillis(500)));
        List<Event> received = new CopyOnWriteArrayList<>();
        client.subscribe("orders", received::add);

        orders.createOrder("alice");
        Thread.sleep(50);   // "it's usually fast"; a longer sleep would pass and cost its full length every run

        assertThat(received).isEmpty();
    }

    @Test
    void pollTakesTheFirstEvent_whichIsAnotherTestsOrder() throws InterruptedException {
        var client = closeLater(new com.study.principles.async.good.EventClient(broker));
        var orders = closeLater(new FakeOrderService(broker, Duration.ofMillis(50)));
        BlockingQueue<Event> received = new LinkedBlockingQueue<>();
        client.subscribe("orders", received::add);

        broker.publish("orders", "order-of-a-parallel-test", "{}");   // noise: the topic is shared
        String myOrder = orders.createOrder("alice");

        Event first = received.poll(2, TimeUnit.SECONDS);
        assertThat(first.key()).isNotEqualTo(myOrder);
        // and EventWaiter would have found it
        assertThat(EventWaiter.pollMatching(received, e -> myOrder.equals(e.key()), Duration.ofSeconds(2))).isPresent();
    }

    // --- 3. Unsubscribe ------------------------------------------------------------------------------

    @Test
    void unsubscribeWithMethodReference_removesNothing() throws InterruptedException {
        var client = closeLater(new com.study.principles.async.good.EventClient(broker));
        BlockingQueue<Event> received = new LinkedBlockingQueue<>();

        client.subscribe("orders", received::add);
        client.unsubscribe("orders", received::add);   // a new object, not equal to the subscribed one

        assertThat(client.listenerCount("orders")).isEqualTo(1);
        broker.publish("orders", "after-unsubscribe", "{}");
        assertThat(received.poll(2, TimeUnit.SECONDS)).isNotNull();
    }

    /**
     * The good client dispatches over a snapshot. A plain unsubscribe followed by releasing the resource
     * leaves a window: the dispatch in progress still calls the listener, which writes to a closed report.
     */
    @Test
    void plainUnsubscribeThenRelease_listenerStillCalledFromTheDispatchInProgress() {
        var client = closeLater(new com.study.principles.async.good.EventClient(broker));
        ReportFile report = new ReportFile();
        CountDownLatch dispatching = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch dispatchDone = new CountDownLatch(1);
        Consumer<Event> writer = e -> report.write(e.key());

        client.subscribe("orders", e -> {   // another subscription, a bit slow
            dispatching.countDown();
            Await.latch(release);
        });
        client.subscribe("orders", writer);
        client.subscribe("orders", e -> dispatchDone.countDown());

        broker.publish("orders", "order-1", "{}");
        Await.latch(dispatching);           // the poll thread is inside the dispatch of order-1

        client.unsubscribe("orders", writer);   // test cleanup...
        report.close();                         // ...then release the resource
        release.countDown();
        Await.latch(dispatchDone);

        assertThat(report.writesAfterClose()).isEqualTo(1);
    }

    /** A volatile flag says "off" but does not wait: the delegate that already passed the check keeps running. */
    @Test
    void volatileFlag_deactivateReturnsWhileTheDelegateStillRuns() throws InterruptedException {
        ReportFile report = new ReportFile();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var listener = new VolatileSwitchableListener<String>(e -> {
            entered.countDown();
            Await.latch(release);
            report.write(e);
        });
        Thread pollThread = new Thread(() -> {
            try {
                listener.accept("order-1");
            } catch (IllegalStateException closed) {
                // the client would log it
            }
        });
        pollThread.start();
        Await.latch(entered);

        listener.deactivate();   // returns at once
        report.close();          // "it's switched off, safe to release"
        release.countDown();
        pollThread.join();

        assertThat(report.writesAfterClose()).isEqualTo(1);
    }

    // --- 4. Client-side smells: bad.EventClient --------------------------------------------------------

    @Test
    void subscribeReturnsBeforeReadingIsReady_eventPublishedRightAfterIsLost() throws InterruptedException {
        var client = closeLater(new EventClient(broker));
        BlockingQueue<Event> received = new LinkedBlockingQueue<>();

        client.subscribe("orders", received::add);
        broker.publish("orders", "order-1", "{}");   // the poll thread positions itself only after this

        assertThat(received.poll(SHORT.toMillis(), TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void oneFailingListener_killsThePollThread_otherSubscriptionsGoSilent() throws InterruptedException {
        var client = closeLater(new EventClient(broker));
        BlockingQueue<Event> received = new LinkedBlockingQueue<>();
        client.subscribe("orders", e -> parseOrder(e.value()));   // somebody's strict parser
        client.subscribe("orders", received::add);
        Await.untilPositioned(broker, "orders", received);

        broker.publish("orders", "garbage", "not json");          // one malformed message in a shared topic
        broker.publish("orders", "order-1", "{\"orderId\":\"order-1\"}");

        assertThat(EventWaiter.pollMatching(received, e -> e.key().equals("order-1"), SHORT)).isEmpty();
    }

    /**
     * A one-shot listener removes itself from the {@code ArrayList} during the dispatch: the iterator throws
     * {@code ConcurrentModificationException} on the poll thread, which dies. (With the self-removing listener
     * second-to-last, there is no exception at all: the last listener is silently skipped.)
     */
    @Test
    void listenerUnsubscribingItselfDuringDispatch_concurrentModificationKillsTheClient() throws InterruptedException {
        var client = closeLater(new EventClient(broker));
        BlockingQueue<Event> received = new LinkedBlockingQueue<>();
        client.subscribe("orders", received::add);
        Await.untilPositioned(broker, "orders", received);
        client.subscribe("orders", new Consumer<>() {   // "wait for the first OrderCreated, then stop listening"
            @Override
            public void accept(Event e) {
                client.unsubscribe("orders", this);
            }
        });

        broker.publish("orders", "order-1", "{}");
        broker.publish("orders", "order-2", "{}");

        assertThat(EventWaiter.pollMatching(received, e -> e.key().equals("order-1"), Duration.ofSeconds(2))).isPresent();
        assertThat(EventWaiter.pollMatching(received, e -> e.key().equals("order-2"), SHORT)).isEmpty();
    }

    static void parseOrder(String json) {
        if (!json.startsWith("{")) {
            throw new IllegalArgumentException("Not JSON: " + json);
        }
    }
}
