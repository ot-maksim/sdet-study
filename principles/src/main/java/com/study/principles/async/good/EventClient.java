package com.study.principles.async.good;

import com.study.principles.async.Event;
import com.study.principles.async.FakeBroker;

import java.lang.System.Logger;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Test client that reads topics in one background thread and hands events to listeners.
 *
 * <pre>{@code
 * try (EventClient client = new EventClient(broker)) {
 *     BlockingQueue<Event> received = new LinkedBlockingQueue<>();
 *     try (var sub = new EventSubscription(client, "orders", received::add)) { // returns when reading is ready
 *         String id = orders.createOrder("alice");
 *         Optional<Event> e = EventWaiter.pollMatching(received, ev -> id.equals(ev.key()), Duration.ofSeconds(5));
 *     }
 * }
 * }</pre>
 *
 * Design (the same as for a real Kafka consumer, see the README):
 * <ul>
 *   <li>All reading and all positions live in one thread, {@code event-client-poll}. A real
 *       {@code KafkaConsumer} is not thread-safe, so other threads send it work through {@link #commands}.</li>
 *   <li>{@link #subscribe} registers the listener first, then asks the poll thread to start at the end of
 *       the topic, and blocks until that is done: once it returns, no new message is missed, and nothing
 *       published before it is delivered.</li>
 *   <li>Listeners run on the poll thread: they must be fast and thread-safe
 *       ({@code BlockingQueue::add}, {@code CopyOnWriteArrayList::add}).</li>
 *   <li>Listeners are kept in a {@link CopyOnWriteArrayList}: dispatch is frequent, subscribing is rare,
 *       and removing a listener during a dispatch is safe. The price: a dispatch iterates over a snapshot,
 *       so a listener removed mid-dispatch can still be called once. {@link SwitchableListener} closes that.</li>
 *   <li>A failing listener is logged and skipped; it does not kill the poll thread or the other listeners.</li>
 * </ul>
 */
public final class EventClient implements AutoCloseable {

    private static final Logger log = System.getLogger(EventClient.class.getName());

    private static final Duration POLL_INTERVAL = Duration.ofMillis(10);
    private static final Duration DEFAULT_SUBSCRIBE_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAX_POLL_RECORDS = 500;

    private final FakeBroker broker;
    private final Map<String, List<Consumer<Event>>> listeners = new ConcurrentHashMap<>();
    private final BlockingQueue<Runnable> commands = new LinkedBlockingQueue<>();
    private final Map<String, Long> positions = new HashMap<>();   // poll thread only
    private final Duration subscribeTimeout;
    private final Thread pollThread;

    private volatile boolean running = true;
    private volatile Throwable fatalError;

    public EventClient(FakeBroker broker) {
        this(broker, DEFAULT_SUBSCRIBE_TIMEOUT);
    }

    public EventClient(FakeBroker broker, Duration subscribeTimeout) {
        this.broker = Objects.requireNonNull(broker, "broker");
        this.subscribeTimeout = subscribeTimeout;
        this.pollThread = new Thread(this::pollLoop, "event-client-poll");
        this.pollThread.setDaemon(true);
        this.pollThread.start();
    }

    /**
     * Subscribes a listener to a topic. Blocks until the poll thread stands at the end of the topic:
     * after it returns, the action that produces the event can run at once.
     *
     * <p>Keep the reference: {@link #unsubscribe} needs the same instance. {@link EventSubscription} does it for you.
     */
    public void subscribe(String topic, Consumer<Event> listener) {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(listener, "listener");
        ensureAlive();

        // Listener first, then "seek to end": no window between "positioned" and "listener ready"
        // in which an event could be read and dropped for lack of listeners.
        listeners.computeIfAbsent(topic, t -> new CopyOnWriteArrayList<>()).add(listener);

        CompletableFuture<Void> ready = new CompletableFuture<>();
        commands.add(() -> {
            try {
                positionAtEnd(topic);
                ready.complete(null);
            } catch (RuntimeException e) {
                ready.completeExceptionally(e);
            }
        });

        try {
            ready.get(subscribeTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            unsubscribe(topic, listener);
            throw new IllegalStateException("Interrupted while subscribing to " + topic, e);
        } catch (ExecutionException e) {
            unsubscribe(topic, listener);
            throw new IllegalStateException("Failed to subscribe to " + topic, e.getCause());
        } catch (TimeoutException e) {
            unsubscribe(topic, listener);
            throw new IllegalStateException("Timed out after " + subscribeTimeout + " subscribing to " + topic, e);
        }
    }

    /** Removes the listener from one topic. Pass the same instance that was subscribed. */
    public void unsubscribe(String topic, Consumer<Event> listener) {
        List<Consumer<Event>> topicListeners = listeners.get(topic);
        if (topicListeners != null) {
            topicListeners.remove(listener);
        }
    }

    public int listenerCount(String topic) {
        List<Consumer<Event>> topicListeners = listeners.get(topic);
        return topicListeners == null ? 0 : topicListeners.size();
    }

    @Override
    public void close() {
        running = false;
        try {
            pollThread.join(DEFAULT_SUBSCRIBE_TIMEOUT.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (pollThread.isAlive()) {
            log.log(Logger.Level.WARNING, "Poll thread did not stop within {0}", DEFAULT_SUBSCRIBE_TIMEOUT);
        }
    }

    // -------------------------------------------------------------------------------------------
    // Everything below runs ONLY on pollThread.
    // -------------------------------------------------------------------------------------------

    private void pollLoop() {
        try {
            while (running) {
                runPendingCommands();
                boolean readSomething = false;
                for (Map.Entry<String, Long> position : positions.entrySet()) {
                    List<Event> events = broker.read(position.getKey(), position.getValue(), MAX_POLL_RECORDS);
                    for (Event event : events) {
                        dispatch(event);
                    }
                    position.setValue(position.getValue() + events.size());
                    readSomething |= !events.isEmpty();
                }
                if (!readSomething) {
                    // Idle: wait for new data, but wake up at once for a subscribe command.
                    Runnable command = commands.poll(POLL_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
                    if (command != null) {
                        command.run();
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            fatalError = t;
            log.log(Logger.Level.ERROR, "Event client poll loop died", t);
        }
    }

    private void runPendingCommands() {
        Runnable command;
        while ((command = commands.poll()) != null) {
            command.run();
        }
    }

    private void positionAtEnd(String topic) {
        // Subscribing to a second topic must not move the first one: keep existing positions.
        // With Kafka this is assign(all partitions) + seekToEnd(new ones) + position(): seekToEnd is lazy,
        // position() forces it now, so a message sent before the first poll() is not skipped.
        positions.computeIfAbsent(topic, broker::endOffset);
    }

    private void dispatch(Event event) {
        List<Consumer<Event>> topicListeners = listeners.get(event.topic());
        if (topicListeners == null) {
            return;
        }
        for (Consumer<Event> listener : topicListeners) {   // iterates over a CopyOnWriteArrayList snapshot
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                // A failing listener must not kill the poll thread or starve the other subscriptions.
                log.log(Logger.Level.WARNING, "Listener {0} failed on {1}@{2}: {3}",
                        listener, event.topic(), event.offset(), e.toString());
            }
        }
    }

    private void ensureAlive() {
        if (fatalError != null) {
            throw new IllegalStateException("Event client is broken", fatalError);
        }
        if (!running || !pollThread.isAlive()) {
            throw new IllegalStateException("Event client is closed");
        }
    }
}
