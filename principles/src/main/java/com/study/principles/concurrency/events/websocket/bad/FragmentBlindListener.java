package com.study.principles.concurrency.events.websocket.bad;

import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * Treats every {@code onText} call as a whole message and ignores {@code last}.
 *
 * <p>Works on a laptop, where messages are small and arrive whole, and breaks on a long payload or a slow
 * network: the JSON arrives in pieces, the parser fails, or worse, a substring match "finds" the order id
 * in half a message.
 */
public final class FragmentBlindListener implements WebSocket.Listener {

    private final Consumer<String> messages;

    public FragmentBlindListener(Consumer<String> messages) {
        this.messages = messages;
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        messages.accept(data.toString());   // a fragment, not a message
        webSocket.request(1);
        return null;
    }
}
