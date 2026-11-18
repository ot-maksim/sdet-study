package com.study.principles.concurrency.events.websocket.good;

import com.study.principles.concurrency.events.websocket.FakeWsServer;
import com.study.principles.concurrency.events.websocket.Messages;

import java.lang.System.Logger;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * WebSocket test client: one connection, one {@link WsSubscription} per channel.
 *
 * <pre>{@code
 * try (var client = WsEventClient.connect(server, Duration.ofSeconds(5));
 *      var orders = client.subscribe("orders")) {            // ready: the server confirmed it
 *     String id = sut.createOrder();
 *     sut.pay(id);
 *     assertThat(orders.await(m -> id.equals(Messages.field(m, "orderId")), Duration.ofSeconds(5))).isPresent();
 * }
 * }</pre>
 *
 * Design:
 * <ul>
 *   <li><b>Ready = acked.</b> {@link #subscribe} registers the subscription locally first, sends the frame, and
 *       blocks until the server's ack (or the timeout). WebSocket has no replay, so this is the only way to know
 *       the next push will arrive.</li>
 *   <li><b>Frames and demand.</b> The listener appends fragments until {@code last}, and asks for the next
 *       invocation in a {@code finally}: a failing dispatch must not stall the connection.</li>
 *   <li><b>A lost connection is loud.</b> {@code onClose} / {@code onError} fail pending subscribes and put a pill
 *       into every subscription's queue: a waiting test fails at once with the close code and reason.</li>
 *   <li><b>No silent reconnect.</b> Messages pushed during a gap are gone; a client that reconnects behind the
 *       test's back makes "not received" ambiguous. The test decides: reconnect and check state via the API.</li>
 *   <li>Every future it waits for has a timeout: the handshake, the send, the ack, the close.</li>
 * </ul>
 */
public final class WsEventClient implements AutoCloseable {

    private static final Logger log = System.getLogger(WsEventClient.class.getName());

    private final Duration timeout;
    private final Map<String, WsSubscription> subscriptions = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Void>> pendingAcks = new ConcurrentHashMap<>();
    private final CompletableFuture<String> closed = new CompletableFuture<>();   // completes with the reason
    private volatile WebSocket ws;

    private WsEventClient(Duration timeout) {
        this.timeout = timeout;
    }

    public static WsEventClient connect(FakeWsServer server, Duration timeout) {
        WsEventClient client = new WsEventClient(timeout);
        try {
            client.ws = server.connect(client.new Listener()).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while connecting", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("WebSocket handshake did not complete within " + timeout, e);
        }
        return client;
    }

    /** Subscribes and blocks until the server confirms: after it returns, no push to the channel is missed. */
    public WsSubscription subscribe(String channel) {
        ensureOpen();
        WsSubscription subscription = new WsSubscription(this, channel);
        if (subscriptions.putIfAbsent(channel, subscription) != null) {
            throw new IllegalStateException("Already subscribed to " + channel);
        }
        CompletableFuture<Void> ack = new CompletableFuture<>();
        pendingAcks.put(channel, ack);
        String lost = closed.getNow(null);
        if (lost != null) {   // closed between ensureOpen and put: connectionLost did not see this ack
            ack.completeExceptionally(new ConnectionLostException(lost));
        }
        try {
            ws.sendText(Messages.subscribe(channel), true).get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            ack.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return subscription;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            forget(channel);
            throw new IllegalStateException("Interrupted while subscribing to " + channel, e);
        } catch (ExecutionException e) {
            forget(channel);
            if (e.getCause() instanceof ConnectionLostException lostWhileSubscribing) {
                throw lostWhileSubscribing;
            }
            String reason = closeReasonIfClosing();
            if (reason != null) {   // the send failed because the server is closing: say why, not "send failed"
                throw new ConnectionLostException(reason);
            }
            throw new IllegalStateException("Failed to subscribe to " + channel, e.getCause());
        } catch (TimeoutException e) {
            forget(channel);
            throw new IllegalStateException("No ack for " + channel + " within " + timeout, e);
        }
    }

    public boolean isOpen() {
        return !closed.isDone();
    }

    /** Graceful close: send the close frame, wait for the server's answer, abort if it does not come. */
    @Override
    public void close() {
        if (closed.isDone()) {
            return;
        }
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "test done");
        try {
            closed.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ws.abort();
        } catch (ExecutionException | TimeoutException e) {
            log.log(Logger.Level.WARNING, "No close answer within {0}, aborting", timeout);
            ws.abort();
        }
    }

    /** Called by {@link WsSubscription#close()}. */
    void unsubscribe(String channel) {
        forget(channel);
        if (isOpen()) {
            // No need to wait: a late message for an unknown channel is dropped in dispatch.
            ws.sendText(Messages.unsubscribe(channel), true);
        }
    }

    private void forget(String channel) {
        subscriptions.remove(channel);
        pendingAcks.remove(channel);
    }

    /** The output closes a moment before onClose arrives: wait for the reason instead of guessing. */
    private String closeReasonIfClosing() {
        if (!ws.isOutputClosed()) {
            return null;
        }
        try {
            return closed.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException | TimeoutException e) {
            return null;
        }
    }

    private void ensureOpen() {
        String reason = closed.getNow(null);
        if (reason != null) {
            throw new ConnectionLostException(reason);
        }
    }

    // -------------------------------------------------------------------------------------------
    // Called ONLY on the io thread (ws-server-io; the HttpClient's executor with a real server).
    // -------------------------------------------------------------------------------------------

    private void dispatch(String message) {
        String channel = Messages.field(message, "channel");
        if ("subscribed".equals(Messages.field(message, "type"))) {
            CompletableFuture<Void> ack = pendingAcks.remove(channel);
            if (ack != null) {
                ack.complete(null);
            }
            return;
        }
        WsSubscription subscription = channel == null ? null : subscriptions.get(channel);
        if (subscription != null) {
            subscription.offer(message);
        }
    }

    private void connectionLost(String reason) {
        if (!closed.complete(reason)) {
            return;
        }
        ConnectionLostException lost = new ConnectionLostException(reason);
        pendingAcks.values().forEach(ack -> ack.completeExceptionally(lost));
        subscriptions.values().forEach(s -> s.connectionLost(reason));
    }

    private final class Listener implements WebSocket.Listener {

        private final StringBuilder partial = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            try {
                partial.append(data);
                if (last) {
                    String message = partial.toString();
                    partial.setLength(0);
                    dispatch(message);
                }
            } catch (RuntimeException e) {
                log.log(Logger.Level.WARNING, "Failed to dispatch a message: {0}", e.toString());
            } finally {
                webSocket.request(1);   // the next invocation, whatever happened to this one
            }
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            connectionLost(statusCode + " " + reason);
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            connectionLost("error: " + error);
        }
    }
}
