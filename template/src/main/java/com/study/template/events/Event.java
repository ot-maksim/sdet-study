package com.study.template.events;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

/**
 * One message read from a topic. The same instance goes to every listener, so it is immutable: no listener can
 * spoil it for the others. The kata's {@code Event} plus {@code partition} (a real topic has several).
 *
 * <p>Headers keep their order and may hold {@code null} values (Kafka allows them), hence no {@code Map.copyOf}.
 */
public record Event(String topic, int partition, long offset, String key, String value,
                    Map<String, String> headers, Instant timestamp) {

    public Event {
        Objects.requireNonNull(topic, "topic");
        Objects.requireNonNull(headers, "headers");
        Objects.requireNonNull(timestamp, "timestamp");
    }

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

    @Override
    public String toString() {
        return topic + "-" + partition + "@" + offset + " " + key + " " + headers + " " + value;
    }
}
