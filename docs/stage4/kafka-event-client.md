# Stage 4 note: Kafka `EventClient` for tests

Deferred from stage 1. The thread-safety part of the design (listener on a foreign thread, `BlockingQueue`,
`pollMatching`, `CopyOnWriteArrayList` registry, `SwitchableListener`, `EventSubscription`, close order) is
taught on a fake broker in the `principles/concurrency/events/kafka` kata
([README](../../principles/src/main/java/com/study/principles/concurrency/events/kafka/README.md)). This note keeps the
Kafka-specific client until stage 4, when a real broker (Testcontainers) is allowed.

**Status:** compiled only against stubs of the `kafka-clients` 3.x signatures (written for 3.9.1).
**Never run against a real broker**: the first task is the integration test below.

## What to reuse from the kata

`SwitchableListener`, `EventSubscription`, `EventWaiter` move as they are (`EventSubscription` takes the Kafka
`EventClient`). `Event` gains `partition` and a factory from `ConsumerRecord`:

```java
public record Event(String topic, int partition, long offset, String key, String value,
                    Map<String, String> headers, Instant timestamp) {

    static Event from(ConsumerRecord<String, String> record) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (Header h : record.headers()) {
            headers.put(h.key(), h.value() == null ? null : new String(h.value(), StandardCharsets.UTF_8));
        }
        return new Event(record.topic(), record.partition(), record.offset(), record.key(), record.value(),
                Collections.unmodifiableMap(headers), Instant.ofEpochMilli(record.timestamp()));
    }

    public String header(String name) {
        return headers.get(name);
    }
}
```

(Kafka header values may be `null`, so no `Map.copyOf` here, unlike the kata's `Event`.)

## Decisions (keep unless there is an explicit reason)

1. **Every `KafkaConsumer` call only from the `event-client-poll` thread.** The consumer is not thread-safe;
   other threads hand it work through `BlockingQueue<Runnable> commands`.
2. **`assign()` + `seekToEnd()`, no `group.id`, no offset commits.** No rebalances, no offsets shared between
   runs; only messages after the subscription are read.
3. **`position()` right after `seekToEnd()`.** `seekToEnd` is lazy; without forcing it, a message sent before
   the first `poll()` is skipped.
4. **`subscribe()` blocks until reading is ready.** The listener is registered *before* the assign, so there
   is no window in which an event is lost.
5. **Listeners in `CopyOnWriteArrayList`** (frequent dispatch, rare subscribe; unsubscribing mid-dispatch is safe).
6. **A failing listener is logged and does not kill the poll thread.**
7. **`SwitchableListener`: `accept` and `deactivate` both `synchronized`** (a volatile flag leaves a check-then-act race).
8. **Close order: `deactivate()` → `unsubscribe()` → release resources.**
9. **Unsubscribe the same instance** (each `received::add` is a new object): `EventSubscription` keeps it.
10. **Tests collect into `LinkedBlockingQueue` or `CopyOnWriteArrayList`**, never `ArrayList`.

## Dependencies (stage 4)

- `org.apache.kafka:kafka-clients` 3.x, version in the parent `pom.xml` only.
- SLF4J comes transitively with `kafka-clients`; add a binding (e.g. `slf4j-simple`, test scope) to see the logs.
- `org.testcontainers:kafka` for the integration test.

## Code

```java
package com.study.framework.events;   // stage 4 package, to be decided

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
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
 * Test client that reads events from Kafka and hands them to subscribers.
 *
 * <pre>{@code
 * try (EventClient client = new EventClient("localhost:9092")) {
 *     BlockingQueue<Event> received = new LinkedBlockingQueue<>();
 *     client.subscribe("orders", received::add);   // returns once reading from the end of the topic is ready
 *     orderApi.createOrder();
 *     Event e = received.poll(10, TimeUnit.SECONDS);
 * }
 * }</pre>
 *
 * Design:
 * <ul>
 *   <li>KafkaConsumer is NOT thread-safe, so every call to it comes from one background thread
 *       ("event-client-poll"). Other threads send it commands through a queue.</li>
 *   <li>{@code assign()} + {@code seekToEnd()} instead of {@code subscribe()} with a group.id:
 *       no rebalances, no offsets shared between runs, only what arrives after the subscription is read.</li>
 *   <li>Listeners run on the poll thread, so they must be fast and thread-safe
 *       (e.g. {@code BlockingQueue::add} or {@code CopyOnWriteArrayList::add}).</li>
 * </ul>
 */
public final class EventClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EventClient.class);

    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);
    private static final Duration KAFKA_CALL_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_SUBSCRIBE_TIMEOUT = Duration.ofSeconds(30);

    private final KafkaConsumer<String, String> consumer;
    private final Map<String, List<Consumer<Event>>> listeners = new ConcurrentHashMap<>();
    private final BlockingQueue<Runnable> commands = new LinkedBlockingQueue<>();
    private final Duration subscribeTimeout;
    private final Thread pollThread;

    private volatile boolean running = true;
    private volatile Throwable fatalError;

    public EventClient(String bootstrapServers) {
        this(bootstrapServers, Map.of(), DEFAULT_SUBSCRIBE_TIMEOUT);
    }

    /**
     * @param extraProps extra consumer settings (SASL, SSL, ...); they override the defaults
     */
    public EventClient(String bootstrapServers, Map<String, Object> extraProps, Duration subscribeTimeout) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, "test-event-client-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        // No group.id: no offset commits, every client reads independently.
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.putAll(extraProps);

        this.consumer = new KafkaConsumer<>(props);
        this.subscribeTimeout = subscribeTimeout;
        this.pollThread = new Thread(this::pollLoop, "event-client-poll");
        this.pollThread.setDaemon(true);
        this.pollThread.start();
    }

    /**
     * Subscribes a listener to a topic. Blocks until the consumer stands at the end of every partition of
     * the topic. After it returns no new message is missed, so the action that produces the event can run at once.
     */
    public void subscribe(String topic, Consumer<Event> listener) {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(listener, "listener");
        ensureAlive();

        // Register the listener first, then seek to the end of the topic:
        // no window between "positioned at the end" and "listener ready" in which an event is lost.
        listeners.computeIfAbsent(topic, t -> new CopyOnWriteArrayList<>()).add(listener);

        CompletableFuture<Void> ready = new CompletableFuture<>();
        commands.add(() -> {
            try {
                assignFromEnd(topic);
                ready.complete(null);
            } catch (Exception e) {
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

    /** Removes the listener from every topic. */
    public void unsubscribe(Consumer<Event> listener) {
        listeners.values().forEach(topicListeners -> topicListeners.remove(listener));
    }

    @Override
    public void close() {
        running = false;
        try {
            pollThread.join(KAFKA_CALL_TIMEOUT.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (pollThread.isAlive()) {
            log.warn("Poll thread did not stop within {}", KAFKA_CALL_TIMEOUT);
        }
    }

    // ---------------------------------------------------------------------------------------
    // Everything below runs ONLY on pollThread.
    // ---------------------------------------------------------------------------------------

    private void pollLoop() {
        try {
            while (running) {
                runPendingCommands();

                if (consumer.assignment().isEmpty()) {
                    // poll() with no assigned partitions throws IllegalStateException,
                    // so just wait for the first subscribe command.
                    Runnable command = commands.poll(POLL_INTERVAL.toMillis(), TimeUnit.MILLISECONDS);
                    if (command != null) {
                        command.run();
                    }
                    continue;
                }

                ConsumerRecords<String, String> records = consumer.poll(POLL_INTERVAL);
                for (ConsumerRecord<String, String> record : records) {
                    dispatch(record);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            fatalError = t;
            log.error("Event client poll loop died", t);
        } finally {
            try {
                consumer.close(KAFKA_CALL_TIMEOUT);
            } catch (Exception e) {
                log.warn("Error while closing Kafka consumer", e);
            }
        }
    }

    private void runPendingCommands() {
        Runnable command;
        while ((command = commands.poll()) != null) {
            command.run();
        }
    }

    private void assignFromEnd(String topic) {
        Set<TopicPartition> current = consumer.assignment();
        boolean alreadyAssigned = current.stream().anyMatch(tp -> tp.topic().equals(topic));
        if (alreadyAssigned) {
            return;
        }

        List<PartitionInfo> partitions = consumer.partitionsFor(topic, KAFKA_CALL_TIMEOUT);
        if (partitions == null || partitions.isEmpty()) {
            throw new IllegalStateException("Topic not found or has no partitions: " + topic);
        }
        List<TopicPartition> newPartitions = partitions.stream()
                .map(p -> new TopicPartition(topic, p.partition()))
                .toList();

        // assign() replaces the whole assignment; positions of partitions already assigned are kept.
        Set<TopicPartition> all = new HashSet<>(current);
        all.addAll(newPartitions);
        consumer.assign(all);

        // seekToEnd() is lazy: the real offset is resolved only at the next poll()/position().
        // Without forcing it, if the producer sends a message before the first poll(),
        // the "end" is computed AFTER that message and the test misses it.
        consumer.seekToEnd(newPartitions);
        for (TopicPartition tp : newPartitions) {
            long position = consumer.position(tp, KAFKA_CALL_TIMEOUT);
            log.debug("Assigned {} at offset {}", tp, position);
        }
    }

    private void dispatch(ConsumerRecord<String, String> record) {
        List<Consumer<Event>> topicListeners = listeners.get(record.topic());
        if (topicListeners == null || topicListeners.isEmpty()) {
            return;
        }
        Event event = Event.from(record);
        for (Consumer<Event> listener : topicListeners) {   // iterates over a CopyOnWriteArrayList snapshot
            try {
                listener.accept(event);
            } catch (RuntimeException e) {
                // A failing listener must not kill the poll thread or break the other subscriptions.
                log.warn("Listener {} failed on {}-{}@{}", listener, record.topic(), record.partition(), record.offset(), e);
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
```

## Integration test to write first (Testcontainers, `org.testcontainers:kafka`)

- an event sent right after `subscribe()` returns is delivered;
- events sent **before** `subscribe()` are not delivered;
- subscribing to a second topic does not reset the position of the first;
- after `EventSubscription.close()` the listener is never called again;
- an exception in one listener does not affect another;
- `subscribe` to a missing topic fails with a clear error (mind the broker's `auto.create.topics.enable`).

Run it, fix what it finds. The kata's `GoodKafkaEventsTest` covers the same list on the fake broker and is a
template for the assertions.

## Usage in a test

```java
class OrderEventsTest {

    static EventClient eventClient;

    @BeforeAll
    static void startClient() {
        eventClient = new EventClient(System.getProperty("kafka.bootstrap", "localhost:9092"));
    }

    @AfterAll
    static void stopClient() {
        eventClient.close();
    }

    @Test
    void orderCreatedEventIsPublished() throws InterruptedException {
        BlockingQueue<Event> received = new LinkedBlockingQueue<>();

        try (var sub = new EventSubscription(eventClient, "orders", received::add)) {
            String orderId = orderApi.createOrder();

            Optional<Event> event = EventWaiter.pollMatching(
                    received,
                    e -> orderId.equals(e.key()),
                    Duration.ofSeconds(10));

            assertThat(event).as("OrderCreated for %s", orderId).isPresent();
        }
    }
}
```

With Awaitility, for a set of events:

```java
List<Event> received = new CopyOnWriteArrayList<>();
try (var sub = new EventSubscription(eventClient, "orders", received::add)) {
    String orderId = orderApi.createOrder();
    await().atMost(10, SECONDS).untilAsserted(() ->
            assertThat(received).anySatisfy(e -> assertThat(e.key()).isEqualTo(orderId)));
}
```

In the stage 4 framework, the client belongs to the composition root (one per run, closed at the end),
not to a `static` field in the test class.
