package com.study.sut.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.sut.users.User;
import com.study.sut.users.UserEvents;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Publishes user events to the {@value #TOPIC} topic.
 *
 * <pre>
 * key     user id ("42"): every event of one user lands in one partition, in order
 * value   the user as JSON, the same shape as GET /api/users/{id}
 * header  event-type: user.created   (one topic for every user event; consumers filter by type)
 * </pre>
 *
 * <p>Fire-and-forget: {@code send} is asynchronous, the HTTP response does not wait for the broker, a failure is
 * only logged. A consumer — and a test — must therefore wait for the event with a timeout, never expect it
 * to be there when the 201 arrives.
 *
 * <p>The topic is created on start, like the table by {@code Schema}: the broker does not auto-create topics,
 * so a typo in a topic name is an error, not a new empty topic.
 */
public final class KafkaUserEvents implements UserEvents, AutoCloseable {

    public static final String TOPIC = "users";
    public static final String TYPE_HEADER = "event-type";
    public static final String CREATED = "user.created";

    private static final Logger LOG = LoggerFactory.getLogger(KafkaUserEvents.class);
    private static final int PARTITIONS = 3;
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(60);

    private final KafkaProducer<String, String> producer;
    private final ObjectMapper json;

    private KafkaUserEvents(KafkaProducer<String, String> producer, ObjectMapper json) {
        this.producer = producer;
        this.json = json;
    }

    /** Creates the topic if needed (waits for the broker up to a minute: compose starts them together) and the producer. */
    public static KafkaUserEvents connect(String bootstrapServers, ObjectMapper json) {
        ensureTopic(bootstrapServers);
        KafkaProducer<String, String> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.CLIENT_ID_CONFIG, "sut-user-events",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                // Defaults of kafka-clients 3.x, written out because they are the point: all replicas, no duplicates
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
                // A request must not hang for a minute when the broker is gone: the event is lost either way
                ProducerConfig.MAX_BLOCK_MS_CONFIG, 5_000));
        LOG.info("Publishing user events to {} at {}", TOPIC, bootstrapServers);
        return new KafkaUserEvents(producer, json);
    }

    @Override
    public void created(User user) {
        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, String.valueOf(user.id()), toJson(user));
        record.headers().add(TYPE_HEADER, CREATED.getBytes(StandardCharsets.UTF_8));
        try {
            producer.send(record, (metadata, error) -> {
                if (error != null) {
                    LOG.error("Lost {} for user {}: {}", CREATED, user.id(), error.toString());
                }
            });
        } catch (RuntimeException e) {
            // send() itself throws when metadata is unavailable for max.block.ms: still not the request's failure
            LOG.error("Lost {} for user {}: {}", CREATED, user.id(), e.toString());
        }
    }

    /** Flushes what is still in the buffer: without it, events of the last requests vanish on shutdown. */
    @Override
    public void close() {
        producer.close(Duration.ofSeconds(5));
    }

    private String toJson(User user) {
        try {
            return json.writeValueAsString(user);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize user " + user.id(), e);
        }
    }

    private static void ensureTopic(String bootstrapServers) {
        try (Admin admin = Admin.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, (int) STARTUP_TIMEOUT.toMillis()))) {
            admin.createTopics(List.of(new NewTopic(TOPIC, PARTITIONS, (short) 1)))
                    .all().get(STARTUP_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            LOG.info("Created topic {} with {} partitions", TOPIC, PARTITIONS);
        } catch (ExecutionException e) {
            if (!(e.getCause() instanceof TopicExistsException)) {
                throw new IllegalStateException("Cannot create topic " + TOPIC + " at " + bootstrapServers, e.getCause());
            }
        } catch (TimeoutException e) {
            throw new IllegalStateException("No Kafka broker at " + bootstrapServers + " within " + STARTUP_TIMEOUT, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
