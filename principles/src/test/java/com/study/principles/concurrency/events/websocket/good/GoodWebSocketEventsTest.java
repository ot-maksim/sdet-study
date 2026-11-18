package com.study.principles.concurrency.events.websocket.good;

import com.study.principles.concurrency.Await;
import com.study.principles.concurrency.events.websocket.FakeOrderStatusService;
import com.study.principles.concurrency.events.websocket.FakeWsServer;
import com.study.principles.concurrency.events.websocket.Messages;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static com.study.principles.concurrency.events.websocket.FakeOrderStatusService.CHANNEL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The scenarios of {@code bad.BadWebSocketEventsTest}, done right. Every test runs on the hard settings:
 * the server takes 100 ms to register a subscription and splits messages into 32-char frames.
 */
class GoodWebSocketEventsTest {

    private static final Duration WAIT = Duration.ofSeconds(2);
    private static final Duration SHORT = Duration.ofMillis(300);

    private final FakeWsServer server = new FakeWsServer(Duration.ofMillis(100), 32);
    private final FakeOrderStatusService orders = new FakeOrderStatusService(server, Duration.ZERO);
    private final WsEventClient client = WsEventClient.connect(server, WAIT);
    private final List<AutoCloseable> toClose = new ArrayList<>(List.of(client, orders, server));

    @AfterEach
    void closeAll() throws Exception {
        for (AutoCloseable c : toClose) {
            c.close();
        }
    }

    private static Predicate<String> isOrder(String orderId) {
        return m -> orderId.equals(Messages.field(m, "orderId"));
    }

    // --- 1. Ready = acked -----------------------------------------------------------------------------

    @Test
    void subscribeReturnsAfterTheServerAck_pushRightAfterIsDelivered() throws InterruptedException {
        try (var sub = client.subscribe(CHANNEL)) {
            assertThat(server.subscriberCount(CHANNEL)).isEqualTo(1);   // already registered on return
            String id = orders.createOrder();
            orders.pay(id);

            assertThat(sub.await(isOrder(id), WAIT))
                    .hasValueSatisfying(m -> assertThat(Messages.field(m, "status")).isEqualTo("PAID"));
        }
    }

    @Test
    void await_skipsOtherTestsOrders() throws InterruptedException {
        try (var sub = client.subscribe(CHANNEL)) {
            String someoneElses = orders.createOrder();
            String mine = orders.createOrder();
            orders.pay(someoneElses);
            orders.pay(mine);

            assertThat(sub.await(isOrder(mine), WAIT)).isPresent();
        }
    }

    // --- 2. Frames and demand ------------------------------------------------------------------------------

    @Test
    void fragmentedMessage_isAssembledBeforeDispatch() throws InterruptedException {
        try (var sub = client.subscribe(CHANNEL)) {
            String id = orders.createOrder();
            orders.pay(id);

            assertThat(sub.await(isOrder(id), WAIT)).contains(Messages.orderStatus(id, "PAID"));   // 80+ chars, 32 per frame
        }
    }

    @Test
    void demandIsRenewed_everyMessageArrivesInOrder() throws InterruptedException {
        try (var sub = client.subscribe(CHANNEL)) {
            for (int i = 0; i < 500; i++) {
                server.publish(CHANNEL, Messages.orderStatus("order-" + i, "PAID"));
            }
            for (int i = 0; i < 500; i++) {
                assertThat(sub.await(m -> true, WAIT)).map(m -> Messages.field(m, "orderId")).contains("order-" + i);
            }
        }
    }

    // --- 3. Losing the connection is loud --------------------------------------------------------------------

    @Test
    void serverClosesTheConnection_awaitFailsAtOnceWithTheReason() throws InterruptedException {
        try (var sub = client.subscribe(CHANNEL)) {
            server.dropAll(1001, "Going away");

            long start = System.nanoTime();
            assertThatThrownBy(() -> sub.await(isOrder("order-1"), Duration.ofSeconds(10)))
                    .isInstanceOf(ConnectionLostException.class)
                    .hasMessage("WebSocket closed (1001 Going away) while waiting on 'orders'");
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));

            assertThatThrownBy(() -> sub.await(isOrder("order-1"), WAIT))   // every next wait too
                    .isInstanceOf(ConnectionLostException.class);
            assertThatThrownBy(() -> client.subscribe("payments"))
                    .isInstanceOf(ConnectionLostException.class);
            assertThat(client.isOpen()).isFalse();
        }
    }

    @Test
    void connectionDropsWhileSubscribing_subscribeFailsWithTheReason_notWithATimeout() {
        server.dropAll(1012, "Service restart");   // processed before the 100 ms subscription completes

        assertThatThrownBy(() -> client.subscribe(CHANNEL))
                .isInstanceOf(ConnectionLostException.class)
                .hasMessageContaining("1012 Service restart");
    }

    // --- 4. No replay: what the transport cannot give -------------------------------------------------------

    @Test
    void pushBeforeSubscribe_isNotReplayed() throws InterruptedException {
        server.publish(CHANNEL, Messages.orderStatus("order-from-before", "PAID"));
        try (var sub = client.subscribe(CHANNEL)) {
            String id = orders.createOrder();
            orders.pay(id);

            assertThat(sub.await(m -> true, WAIT)).map(m -> Messages.field(m, "orderId")).contains(id);   // the first one is mine
        }
    }

    @Test
    void afterReconnect_pushFromTheGapIsGone_soCheckTheStateViaTheApi() throws InterruptedException {
        String id = orders.createOrder();
        try (var sub = client.subscribe(CHANNEL)) {
            server.dropAll(1001, "Going away");
            Await.until(() -> !client.isOpen(), WAIT);
        }

        orders.pay(id);                                       // pushed while nobody is connected
        Await.until(() -> orders.pushesSent() == 1, WAIT);

        try (var again = WsEventClient.connect(server, WAIT);
             var sub = again.subscribe(CHANNEL)) {
            assertThat(sub.await(isOrder(id), SHORT)).as("no replay after reconnect").isEmpty();
            assertThat(orders.status(id)).isEqualTo("PAID");  // the state is not lost: assert it through the API
        }
    }

    // --- 5. Unsubscribe and close ------------------------------------------------------------------------------

    @Test
    void closingTheSubscription_unsubscribesOnTheServer() {
        try (var sub = client.subscribe(CHANNEL)) {
            assertThat(server.subscriberCount(CHANNEL)).isEqualTo(1);
        }
        Await.until(() -> server.subscriberCount(CHANNEL) == 0, WAIT);
    }

    @Test
    void closingTheClient_closesTheConnection_andLaterUseFailsFast() {
        client.close();

        assertThat(server.connectionCount()).isZero();
        assertThatThrownBy(() -> client.subscribe(CHANNEL))
                .isInstanceOf(ConnectionLostException.class)
                .hasMessage("WebSocket closed (1000 test done)");
    }
}
