package com.study.principles.concurrency.events.websocket;

import java.io.IOException;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * An in-memory WebSocket server that speaks {@link Messages}' protocol and hands out the <b>real JDK</b>
 * {@link java.net.http.WebSocket} interface: {@link #connect} stands in for
 * {@code HttpClient.newWebSocketBuilder().buildAsync(uri, listener)}, and the clients' listeners are plain
 * {@link WebSocket.Listener}s. So the JDK's own rules apply, and so do its traps:
 * <ul>
 *   <li><b>Push, no replay.</b> A message reaches only the connections subscribed to its channel <i>at the moment
 *       it is published</i>. Nothing is stored: a message nobody was subscribed to is gone.</li>
 *   <li><b>Subscribing takes time.</b> The server registers a subscription after {@code subscribeLatency} and then
 *       sends the ack; pushes before that do not reach the client. Sending the subscribe frame is not being subscribed.</li>
 *   <li><b>Demand.</b> Like the JDK, every listener invocation ({@code onText} per fragment) consumes one unit of
 *       demand from {@link WebSocket#request(long)}. A listener that never calls {@code request} again gets
 *       one message and then nothing, forever and silently.</li>
 *   <li><b>Fragments.</b> Messages longer than {@code maxFrameChars} arrive as several {@code onText(data, last)}
 *       calls; only the one with {@code last == true} ends the message.</li>
 *   <li><b>The server can go away.</b> {@link #dropAll} closes every connection (a restart, a deploy, an idle
 *       timeout on a proxy): the listeners get {@code onClose}.</li>
 * </ul>
 * All listener callbacks run on one thread, {@code ws-server-io}: a foreign thread for the test, as the
 * {@code HttpClient}'s executor is with a real server.
 */
public final class FakeWsServer implements AutoCloseable {

    private final ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ws-server-io");
        t.setDaemon(true);
        return t;
    });
    private final Set<Connection> connections = ConcurrentHashMap.newKeySet();
    private final Supplier<Duration> subscribeLatency;
    private final int maxFrameChars;

    /** Instant subscriptions, whole messages. */
    public FakeWsServer() {
        this(Duration.ZERO, Integer.MAX_VALUE);
    }

    public FakeWsServer(Duration subscribeLatency, int maxFrameChars) {
        this(() -> subscribeLatency, maxFrameChars);
    }

    public FakeWsServer(Supplier<Duration> subscribeLatency, int maxFrameChars) {
        if (maxFrameChars <= 0) {
            throw new IllegalArgumentException("maxFrameChars must be positive: " + maxFrameChars);
        }
        this.subscribeLatency = subscribeLatency;
        this.maxFrameChars = maxFrameChars;
    }

    /** The handshake: {@code onOpen} is called on the io thread, then the future completes. */
    public CompletableFuture<WebSocket> connect(WebSocket.Listener listener) {
        Connection connection = new Connection(listener);
        CompletableFuture<WebSocket> handshake = new CompletableFuture<>();
        io.execute(() -> {
            connections.add(connection);
            listener.onOpen(connection);
            handshake.complete(connection);
        });
        return handshake;
    }

    /** Pushes a message to every connection subscribed to the channel right now. */
    public void publish(String channel, String text) {
        io.execute(() -> {
            for (Connection c : connections) {
                if (c.channels.contains(channel)) {
                    c.push(text);
                }
            }
        });
    }

    /** Closes every connection from the server side, as a restart would. */
    public void dropAll(int statusCode, String reason) {
        io.execute(() -> List.copyOf(connections).forEach(c -> c.closeFromServer(statusCode, reason)));
    }

    public int subscriberCount(String channel) {
        return (int) connections.stream().filter(c -> c.channels.contains(channel)).count();
    }

    public int connectionCount() {
        return connections.size();
    }

    @Override
    public void close() {
        io.shutdownNow();
    }

    private record Frame(String text, boolean last) {
    }

    /** The client's handle on one connection. Fields marked "io" are touched only by the io thread. */
    private final class Connection implements WebSocket {

        private final Listener listener;
        private final Set<String> channels = ConcurrentHashMap.newKeySet();   // read by subscriberCount
        private final Deque<Frame> outbound = new ArrayDeque<>();             // io
        private long demand;                                                  // io
        private volatile boolean open = true;

        Connection(Listener listener) {
            this.listener = listener;
        }

        /** io: split into frames, deliver as far as the demand allows. */
        void push(String text) {
            for (int from = 0; from < text.length(); from += maxFrameChars) {
                int to = (int) Math.min((long) from + maxFrameChars, text.length());
                outbound.add(new Frame(text.substring(from, to), to == text.length()));
            }
            drain();
        }

        /** io */
        private void drain() {
            while (open && demand > 0 && !outbound.isEmpty()) {
                Frame frame = outbound.poll();
                demand--;
                listener.onText(this, frame.text(), frame.last());
            }
        }

        /** io */
        private void onClientMessage(String text) {
            String op = Messages.field(text, "op");
            String channel = Messages.field(text, "channel");
            if ("subscribe".equals(op)) {
                io.schedule(() -> {
                    if (open) {
                        channels.add(channel);
                        push(Messages.subscribed(channel));
                    }
                }, subscribeLatency.get().toNanos(), TimeUnit.NANOSECONDS);
            } else if ("unsubscribe".equals(op)) {
                channels.remove(channel);
            }
        }

        /** io */
        void closeFromServer(int statusCode, String reason) {
            if (!open) {
                return;
            }
            open = false;
            connections.remove(this);
            outbound.clear();
            listener.onClose(this, statusCode, reason);
        }

        @Override
        public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            if (!open) {
                return CompletableFuture.failedFuture(new IOException("Output closed"));
            }
            String text = data.toString();
            io.execute(() -> onClientMessage(text));
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
            // The server answers with its own close frame: the client's listener sees onClose.
            io.execute(() -> closeFromServer(statusCode, reason));
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public void request(long n) {
            if (n <= 0) {
                throw new IllegalArgumentException("Non-positive demand: " + n);
            }
            io.execute(() -> {
                demand += n;
                drain();
            });
        }

        @Override
        public void abort() {
            open = false;
            connections.remove(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public String getSubprotocol() {
            return "";
        }

        @Override
        public boolean isOutputClosed() {
            return !open;
        }

        @Override
        public boolean isInputClosed() {
            return !open;
        }
    }
}
