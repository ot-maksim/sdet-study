package com.study.project.events;

import java.util.function.Consumer;

/**
 * A subscription that always unsubscribes correctly — moved from the kata, now over the Kafka {@link EventClient}.
 *
 * <pre>{@code
 * try (var sub = new EventSubscription(client, "users", received::add)) {
 *     ...
 * } // deactivate() + unsubscribe(), even if an assertion failed
 * }</pre>
 *
 * It keeps the wrapper itself, so a method reference can be passed straight in: unsubscribing removes the very
 * instance that was subscribed ({@code received::add} twice is two different objects).
 */
public final class EventSubscription implements AutoCloseable {

    private final EventClient client;
    private final String topic;
    private final SwitchableListener<Event> listener;

    /** Returns once reading is ready: an event produced after this constructor is not missed. */
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
