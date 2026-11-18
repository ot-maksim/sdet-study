package com.study.principles.concurrency.events.kafka.good;

import com.study.principles.concurrency.events.SwitchableListener;
import com.study.principles.concurrency.events.kafka.Event;

import java.util.function.Consumer;

/**
 * A subscription that always unsubscribes correctly.
 *
 * <pre>{@code
 * try (var sub = new EventSubscription(client, "orders", received::add)) {
 *     ...
 * } // deactivate() + unsubscribe(), even if an assertion failed
 * }</pre>
 *
 * It keeps the reference to the wrapper itself, so a method reference can be passed straight in:
 * unsubscribing always removes the very instance that was subscribed.
 */
public final class EventSubscription implements AutoCloseable {

    private final EventClient client;
    private final String topic;
    private final SwitchableListener<Event> listener;

    public EventSubscription(EventClient client, String topic, Consumer<Event> logic) {
        this.client = client;
        this.topic = topic;
        this.listener = new SwitchableListener<>(logic);
        client.subscribe(topic, listener);
    }

    /** Order matters: switch off first (no more calls, the running one finished), then leave the list. */
    @Override
    public void close() {
        listener.deactivate();
        client.unsubscribe(topic, listener);
    }
}
