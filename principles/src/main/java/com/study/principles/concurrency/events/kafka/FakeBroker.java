package com.study.principles.concurrency.events.kafka;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An in-memory "Kafka": every topic is an append-only log with offsets 0, 1, 2... One partition per topic.
 *
 * <p>The broker only stores; it never calls anybody. Like a real consumer, a client has to <b>poll</b> it
 * from its own thread and keep its own position. That is what makes the listener callbacks arrive on a
 * foreign thread, which is the whole point of the kata.
 *
 * <p>Topics are not auto-created (as with {@code auto.create.topics.enable=false}): publishing to or
 * reading from an unknown topic fails. Thread-safe: every method holds the broker's monitor.
 */
public final class FakeBroker {

    private final Map<String, List<Event>> topics = new HashMap<>();   // guarded by this
    private final Clock clock;

    public FakeBroker() {
        this(Clock.systemUTC());
    }

    public FakeBroker(Clock clock) {
        this.clock = clock;
    }

    public FakeBroker createTopics(String... names) {
        synchronized (this) {
            for (String name : names) {
                topics.putIfAbsent(name, new ArrayList<>());
            }
        }
        return this;
    }

    public long publish(String topic, String key, String value) {
        return publish(topic, key, value, Map.of());
    }

    /** Appends a message and returns its offset. */
    public synchronized long publish(String topic, String key, String value, Map<String, String> headers) {
        List<Event> log = log(topic);
        long offset = log.size();
        log.add(new Event(topic, offset, key, value, headers, clock.instant()));
        return offset;
    }

    /** The offset the next message will get ("seek to end" lands here). */
    public synchronized long endOffset(String topic) {
        return log(topic).size();
    }

    /** Up to {@code max} messages starting at {@code fromOffset}; empty if there is nothing new. */
    public synchronized List<Event> read(String topic, long fromOffset, int max) {
        List<Event> log = log(topic);
        int from = (int) Math.min(fromOffset, log.size());
        int to = Math.min(from + max, log.size());
        return List.copyOf(log.subList(from, to));
    }

    private List<Event> log(String topic) {
        List<Event> log = topics.get(topic);
        if (log == null) {
            throw new IllegalArgumentException("Unknown topic: " + topic);
        }
        return log;
    }
}
