package com.study.principles.concurrency.events.websocket.bad;

import com.study.principles.concurrency.events.websocket.FakeWsServer;
import com.study.principles.concurrency.events.websocket.Messages;

import java.net.http.WebSocket;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * A typical first WebSocket test client. Frames and demand are handled right (those traps are shown on their
 * own in {@link NoDemandListener} and {@link FragmentBlindListener}); the lifecycle is not:
 * <ul>
 *   <li><b>{@code subscribe} = "the frame was sent".</b> The server registers the subscription later and says so
 *       with an ack, which this client ignores. A push right after {@code subscribe} returns goes to nobody, and
 *       WebSocket has no replay: tests compensate with a warm-up {@code sleep}.</li>
 *   <li><b>{@code onClose} / {@code onError} not handled.</b> When the server goes away, nobody is told: a waiting
 *       test runs into its full timeout and reports "event not received", blaming the SUT.</li>
 *   <li><b>Futures dropped.</b> {@code sendText} returns a {@code CompletableFuture}; ignoring it hides a failed
 *       send, so {@code subscribe} on a dead connection "succeeds".</li>
 *   <li><b>{@code join()} without a timeout</b> on the handshake: a server that never answers hangs the suite.</li>
 * </ul>
 */
public final class WsEventClient implements AutoCloseable {

    private final Map<String, Consumer<String>> listeners = new ConcurrentHashMap<>();
    private final WebSocket ws;

    public WsEventClient(FakeWsServer server) {
        this.ws = server.connect(new Listener()).join();
    }

    /** Returns as soon as the subscribe frame is handed over. */
    public void subscribe(String channel, Consumer<String> listener) {
        listeners.put(channel, listener);
        ws.sendText(Messages.subscribe(channel), true);
    }

    @Override
    public void close() {
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "");
    }

    private final class Listener implements WebSocket.Listener {

        private final StringBuilder partial = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) {
                String message = partial.toString();
                partial.setLength(0);
                if (!"subscribed".equals(Messages.field(message, "type"))) {   // acks: nobody waits for them
                    Consumer<String> listener = listeners.get(Messages.field(message, "channel"));
                    if (listener != null) {
                        listener.accept(message);
                    }
                }
            }
            webSocket.request(1);
            return null;
        }

        // onClose and onError: the defaults, which do nothing.
    }
}
