package com.study.principles.concurrency.events.websocket.bad;

import com.study.principles.concurrency.Await;
import com.study.principles.concurrency.events.EventWaiter;
import com.study.principles.concurrency.events.websocket.FakeOrderStatusService;
import com.study.principles.concurrency.events.websocket.FakeWsServer;
import com.study.principles.concurrency.events.websocket.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static com.study.principles.concurrency.events.websocket.FakeOrderStatusService.CHANNEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Characterization tests: they pass <i>because</i> they pin the bugs of {@link WsEventClient},
 * {@link NoDemandListener} and {@link FragmentBlindListener}.
 */
class BadWebSocketEventsTest {

    private static final Duration WAIT = Duration.ofSeconds(2);
    private static final Duration SHORT = Duration.ofMillis(500);

    private final List<AutoCloseable> toClose = new ArrayList<>();

    /** Reverse order, as try-with-resources: clients before the server they talk to. */
    @AfterEach
    void closeAll() throws Exception {
        for (int i = toClose.size() - 1; i >= 0; i--) {
            toClose.get(i).close();
        }
    }

    private <T extends AutoCloseable> T closeLater(T closeable) {
        toClose.add(closeable);
        return closeable;
    }

    private static Predicate<String> isOrder(String orderId) {
        return m -> orderId.equals(Messages.field(m, "orderId"));
    }

    // --- 1. Ready is not "the frame was sent" ------------------------------------------------------------

    @Test
    void subscribeWithoutWaitingForTheAck_pushRightAfterGoesToNobody() throws InterruptedException {
        var server = closeLater(new FakeWsServer(Duration.ofMillis(200), Integer.MAX_VALUE));
        var orders = closeLater(new FakeOrderStatusService(server, Duration.ZERO));
        var client = closeLater(new WsEventClient(server));
        BlockingQueue<String> received = new LinkedBlockingQueue<>();

        client.subscribe(CHANNEL, received::add);   // returns at once; the server registers it in 200 ms
        String id = orders.createOrder();
        orders.pay(id);

        assertThat(EventWaiter.pollMatching(received, isOrder(id), SHORT)).isEmpty();
        assertThat(orders.status(id)).isEqualTo("PAID");   // the SUT did its job; the push had no subscriber
    }

    // --- 2. A lost connection nobody hears about -----------------------------------------------------------

    @Test
    void serverClosesTheConnection_testWaitsItsFullTimeout_andBlamesTheSut() throws InterruptedException {
        var server = closeLater(new FakeWsServer());
        var orders = closeLater(new FakeOrderStatusService(server, Duration.ZERO));
        var client = closeLater(new WsEventClient(server));
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        client.subscribe(CHANNEL, received::add);
        Await.until(() -> server.subscriberCount(CHANNEL) == 1, WAIT);

        server.dropAll(1001, "Going away");   // a restart while the test waits
        Await.until(() -> server.connectionCount() == 0, WAIT);
        String id = orders.createOrder();
        orders.pay(id);

        long start = System.nanoTime();
        assertThat(EventWaiter.pollMatching(received, isOrder(id), SHORT)).as("reads as: no push for %s", id).isEmpty();
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(SHORT);
        // and the dead client still "works": the failed send is in a future nobody looked at
        assertThatCode(() -> client.subscribe("payments", m -> { })).doesNotThrowAnyException();
    }

    // --- 3. JDK listener traps -------------------------------------------------------------------------

    @Test
    void listenerWithoutRequest_getsOneMessageAndThenNothing() throws Exception {
        var server = closeLater(new FakeWsServer());
        BlockingQueue<String> received = new LinkedBlockingQueue<>();
        WebSocket ws = server.connect(new NoDemandListener(received::add)).get(2, TimeUnit.SECONDS);
        ws.sendText(Messages.subscribe(CHANNEL), true);
        Await.until(() -> server.subscriberCount(CHANNEL) == 1, WAIT);

        server.publish(CHANNEL, Messages.orderStatus("order-1", "PAID"));
        server.publish(CHANNEL, Messages.orderStatus("order-2", "PAID"));

        assertThat(received.poll(WAIT.toMillis(), TimeUnit.MILLISECONDS)).isEqualTo(Messages.subscribed(CHANNEL));
        assertThat(received.poll(SHORT.toMillis(), TimeUnit.MILLISECONDS)).as("the ack used up the only demand").isNull();
    }

    @Test
    void listenerIgnoringLast_seesPiecesInsteadOfMessages() throws Exception {
        var server = closeLater(new FakeWsServer(Duration.ZERO, 32));
        List<String> received = new CopyOnWriteArrayList<>();
        WebSocket ws = server.connect(new FragmentBlindListener(received::add)).get(2, TimeUnit.SECONDS);
        ws.sendText(Messages.subscribe(CHANNEL), true);
        Await.until(() -> server.subscriberCount(CHANNEL) == 1, WAIT);

        String event = Messages.orderStatus("order-1", "PAID");
        server.publish(CHANNEL, event);
        Await.until(() -> String.join("", received).endsWith(event), WAIT);

        assertThat(received).doesNotContain(event)
                .noneMatch(piece -> piece.startsWith("{") && piece.endsWith("}"));   // not one parseable message
    }
}
