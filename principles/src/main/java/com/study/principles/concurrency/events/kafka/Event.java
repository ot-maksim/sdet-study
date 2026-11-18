package com.study.principles.concurrency.events.kafka;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * One message read from a topic. The same instance is handed to every listener, so it is immutable:
 * no listener can spoil the event for the others (headers are copied into an unmodifiable map).
 *
 * <p>Mirrors what a Kafka test client would build from a {@code ConsumerRecord<String, String>}; the fake
 * broker has one partition per topic, so there is no partition field.
 */
public record Event(String topic, long offset, String key, String value, Map<String, String> headers,
                    Instant timestamp) {

    public Event {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(timestamp, "timestamp");
        headers = Map.copyOf(headers);
    }

    public String header(String name) {
        return headers.get(name);
    }
}
