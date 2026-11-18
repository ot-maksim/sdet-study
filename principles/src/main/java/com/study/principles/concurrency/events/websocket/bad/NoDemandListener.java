package com.study.principles.concurrency.events.websocket.bad;

import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * Overrides {@code onText} and forgets {@code webSocket.request(1)}.
 *
 * <p>The JDK delivers exactly as many listener invocations as were requested. The default {@code onOpen} requests
 * one, the default {@code onText} requests the next; an override that does not call {@code request} receives
 * <b>one</b> message and then nothing. No exception, no log: the messages wait on the other side, and the
 * test times out.
 */
public final class NoDemandListener implements WebSocket.Listener {

    private final Consumer<String> messages;

    public NoDemandListener(Consumer<String> messages) {
        this.messages = messages;
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        messages.accept(data.toString());
        return null;
    }
}
