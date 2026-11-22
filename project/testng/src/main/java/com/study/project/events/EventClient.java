package com.study.project.events;

import java.time.Duration;
import java.util.HashMap;
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
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test client that reads events from Kafka and hands them to subscribers. No Spring in here: the root
 * ({@code ProjectConfiguration}) builds one per run and closes it at the end.
 *
 * <pre>{@code
 * try (var sub = new EventSubscription(client, "users", received::add)) {   // returns once reading is ready
 *     userApi.create(...);
 *     Optional<Event> e = EventWaiter.pollMatching(received, ev -> id.equals(ev.key()), Duration.ofSeconds(10));
 * }
 * }</pre>
 *
 * Design (the note {@code docs/stage4/kafka-event-client.md} of this repository, checked against a real broker in {@code EventClientTest}):
 * <ul>
 *   <li>KafkaConsumer is NOT thread-safe, so every call to it comes from one background thread
 *       ("event-client-poll"). Other threads send it commands through a queue.</li>
 *   <li>{@code assign()} + {@code seekToEnd()} instead of {@code subscribe()} with a group.id:
 *       no rebalances, no offsets shared between runs, only what arrives after the subscription is read.</li>
 *   <li><b>Every listener has its own start</b>: the end offset of each partition at the moment it subscribed.
 *       The consumer's position is shared by all listeners of a topic; for a topic that is already assigned it
 *       may still be behind records not yet polled, and without the per-listener start a late listener got them
 *       (found by the real broker, not by the kata's fake one).</li>
 *   <li>Registration happens on the poll thread between two polls, so there is no moment in which the listener is
 *       registered but its start is unknown, or the start is known but the listener not yet registered.</li>
 *   <li>Listeners run on the poll thread, so they must be fast and thread-safe
 *       (e.g. {@code BlockingQueue::add} or {@code CopyOnWriteArrayList::add}); a failing one is logged.</li>
 * </ul>
 */
public final class EventClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(EventClient.class);

    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);
    private static final Duration KAFKA_CALL_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_SUBSCRIBE_TIMEOUT = Duration.ofSeconds(30);
    /** A topic created a moment ago may not be in the broker's metadata yet; after this it is "not found". */
    private static final Duration NEW_TOPIC_GRACE = Duration.ofSeconds(3);

    private final String bootstrapServers;
    private final KafkaConsumer<String, String> consumer;
    private final Map<String, List<Registration>> listeners = new ConcurrentHashMap<>();
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
        // A test client must never create a topic by asking for it: a typo is an error, not an empty topic.
        props.put(ConsumerConfig.ALLOW_AUTO_CREATE_TOPICS_CONFIG, false);
        props.putAll(extraProps);

        this.bootstrapServers = bootstrapServers;
        this.consumer = new KafkaConsumer<>(props);
        this.subscribeTimeout = subscribeTimeout;
        this.pollThread = new Thread(this::pollLoop, "event-client-poll");
        this.pollThread.setDaemon(true);
        this.pollThread.start();
    }

    /**
     * Subscribes a listener to a topic. Blocks until the listener is registered with the current end of every
     * partition as its start. After it returns no new message is missed and no older one is delivered, so the
     * action that produces the event can run at once.
     */
    public void subscribe(String topic, Consumer<Event> listener) {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(listener, "listener");
        ensureAlive();

        CompletableFuture<Void> ready = new CompletableFuture<>();
        commands.add(() -> {
            try {
                Map<Integer, Long> start = assignAndFindEnd(topic);
                listeners.computeIfAbsent(topic, t -> new CopyOnWriteArrayList<>()).add(new Registration(listener, start));
                if (!ready.complete(null)) {
                    unsubscribe(topic, listener);   // the caller gave up waiting: do not leave an orphan behind
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();   // the poll loop sees it and stops
                ready.completeExceptionally(e);
            } catch (Exception e) {
                ready.completeExceptionally(e);
            }
        });

        try {
            ready.get(subscribeTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            ready.cancel(false);
            throw new IllegalStateException("Interrupted while subscribing to " + topic, e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to subscribe to " + topic + " at " + bootstrapServers + ": "
                    + e.getCause().getMessage(), e.getCause());
        } catch (TimeoutException e) {
            ready.cancel(false);
            throw new IllegalStateException("Timed out after " + subscribeTimeout + " subscribing to " + topic
                    + " at " + bootstrapServers, e);
        }
    }

    /** Removes the listener from one topic. Pass the same instance that was subscribed. */
    public void unsubscribe(String topic, Consumer<Event> listener) {
        List<Registration> topicListeners = listeners.get(topic);
        if (topicListeners != null) {
            topicListeners.removeIf(r -> r.listener() == listener);
        }
    }

    /** Removes the listener from every topic. */
    public void unsubscribe(Consumer<Event> listener) {
        listeners.values().forEach(topicListeners -> topicListeners.removeIf(r -> r.listener() == listener));
    }

    /** Idempotent: Spring calls it at the end of the run, a test may have called it before. */
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

    @Override
    public String toString() {
        return "EventClient(" + bootstrapServers + ")";
    }

    /** A listener and where it starts: partition → the first offset it may see. */
    private record Registration(Consumer<Event> listener, Map<Integer, Long> start) {

        boolean wants(ConsumerRecord<?, ?> record) {
            return record.offset() >= start.getOrDefault(record.partition(), Long.MAX_VALUE);
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

    /** Assigns the topic if needed (from its end) and returns the end of every partition: the new listener's start. */
    private Map<Integer, Long> assignAndFindEnd(String topic) throws InterruptedException {
        List<PartitionInfo> partitions = partitionsOf(topic);
        List<TopicPartition> topicPartitions = partitions.stream()
                .map(p -> new TopicPartition(topic, p.partition()))
                .toList();

        Set<TopicPartition> current = consumer.assignment();
        if (!current.containsAll(topicPartitions)) {
            List<TopicPartition> added = topicPartitions.stream().filter(tp -> !current.contains(tp)).toList();
            // assign() replaces the whole assignment; positions of partitions already assigned are kept.
            Set<TopicPartition> all = new HashSet<>(current);
            all.addAll(added);
            consumer.assign(all);
            // seekToEnd() is lazy: the offset is resolved only at the next poll()/position(). Without forcing it,
            // a message sent before the first poll() moves the "end" past itself and is missed.
            consumer.seekToEnd(added);
            for (TopicPartition tp : added) {
                log.debug("Assigned {} at offset {}", tp, consumer.position(tp, KAFKA_CALL_TIMEOUT));
            }
        }

        // The listener's own start: for a topic assigned long ago the shared position may be behind the end.
        Map<Integer, Long> start = new HashMap<>();
        consumer.endOffsets(topicPartitions, KAFKA_CALL_TIMEOUT).forEach((tp, end) -> start.put(tp.partition(), end));
        return Map.copyOf(start);
    }

    /**
     * The topic's partitions, waiting {@link #NEW_TOPIC_GRACE} for a topic that is not known yet: createTopics()
     * returns when the controller has the topic, the broker answering metadata may learn it a moment later (found
     * by EventClientTest: a topic created right before subscribe() was "not found" now and then). The poll thread
     * waits here, so other listeners get their events up to that much later.
     */
    private List<PartitionInfo> partitionsOf(String topic) throws InterruptedException {
        long deadline = System.nanoTime() + NEW_TOPIC_GRACE.toNanos();
        while (true) {
            List<PartitionInfo> partitions = consumer.partitionsFor(topic, KAFKA_CALL_TIMEOUT);
            if (partitions != null && !partitions.isEmpty()) {
                return partitions;
            }
            if (System.nanoTime() > deadline) {
                throw new IllegalStateException("Topic not found or has no partitions within " + NEW_TOPIC_GRACE + ": " + topic);
            }
            Thread.sleep(POLL_INTERVAL.toMillis());
        }
    }

    private void dispatch(ConsumerRecord<String, String> record) {
        List<Registration> topicListeners = listeners.get(record.topic());
        if (topicListeners == null || topicListeners.isEmpty()) {
            return;
        }
        Event event = null;
        for (Registration registration : topicListeners) {   // iterates over a CopyOnWriteArrayList snapshot
            if (!registration.wants(record)) {
                continue;
            }
            if (event == null) {
                event = Event.from(record);
            }
            try {
                registration.listener().accept(event);
            } catch (RuntimeException e) {
                // A failing listener must not kill the poll thread or break the other subscriptions.
                log.warn("Listener {} failed on {}-{}@{}", registration.listener(), record.topic(), record.partition(),
                        record.offset(), e);
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
