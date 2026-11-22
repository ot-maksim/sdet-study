package com.study.project.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.testng.SkipException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.kafka.KafkaContainer;

/**
 * The client against a real broker — the list from {@code docs/stage4/kafka-event-client.md}, the same properties
 * the kata's {@code GoodKafkaEventsTest} checks on the fake broker.
 *
 * <p>A component test owns its dependency: one broker per class, started here, not the run's environment (that one
 * belongs to E2E tests and may be a stand without Kafka). Auto-creation of topics is off, as on the SUT's broker.
 * Every test makes its own topic (3 partitions) and its own client, so the tests run in parallel.
 *
 * <p>No sleeps for "nothing arrived": a second listener on the same topic is the barrier — once it has the
 * event, the dispatch of that record is over for every listener.
 */
public class EventClientTest {

    /** Same image as {@code project.containers.kafka-image}. */
    static final String KAFKA_IMAGE = "apache/kafka-native:3.9.1";
    static final Duration WAIT = Duration.ofSeconds(10);

    static KafkaContainer kafka;
    static KafkaProducer<String, String> producer;
    static Admin admin;

    @BeforeClass
    public void startBroker() {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            throw new SkipException("Docker is not available");   // every test of the class is skipped
        }
        kafka = new KafkaContainer(KAFKA_IMAGE).withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");
        kafka.start();
        producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName()));
        admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()));
    }

    @AfterClass(alwaysRun = true)
    public void stopBroker() {
        if (producer != null) {
            producer.close();
        }
        if (admin != null) {
            admin.close();
        }
        if (kafka != null) {
            kafka.stop();
        }
    }

    @Test
    public void eventSentRightAfterSubscribeIsDelivered() throws Exception {
        String topic = newTopic();
        try (EventClient client = newClient()) {
            BlockingQueue<Event> received = new LinkedBlockingQueue<>();
            client.subscribe(topic, received::add);

            send(topic, "42", "created");   // no pause in between: subscribe() returned = reading is ready

            Event event = received.poll(WAIT.toMillis(), TimeUnit.MILLISECONDS);
            assertThat(event).isNotNull();
            assertThat(event.topic()).isEqualTo(topic);
            assertThat(event.key()).isEqualTo("42");
            assertThat(event.value()).isEqualTo("created");
            assertThat(event.header("event-type")).isEqualTo("test");
        }
    }

    @Test
    public void eventsSentBeforeSubscribeAreNotDelivered() throws Exception {
        String topic = newTopic();
        // One key = one partition = one order: anything old that leaked would arrive before "after"
        for (int i = 0; i < 10; i++) {
            send(topic, "k", "before-" + i);
        }
        try (EventClient client = newClient()) {
            BlockingQueue<Event> received = new LinkedBlockingQueue<>();
            client.subscribe(topic, received::add);

            send(topic, "k", "after");

            assertThat(received.poll(WAIT.toMillis(), TimeUnit.MILLISECONDS)).extracting(Event::value).isEqualTo("after");
            assertThat(received).isEmpty();
        }
    }

    @Test
    public void subscribingToSecondTopicDoesNotResetTheFirst() throws Exception {
        String first = newTopic();
        String second = newTopic();
        try (EventClient client = newClient()) {
            BlockingQueue<Event> received = new LinkedBlockingQueue<>();
            client.subscribe(first, received::add);
            send(first, "k", "1");

            client.subscribe(second, received::add);   // assign() replaces the assignment: positions must survive
            send(first, "k", "2");
            send(second, "k", "3");

            // not lost (reset to end), not twice (reset to start); the barrier on the second topic comes last
            assertThat(drainEvents(received, 3)).extracting(Event::value).containsExactlyInAnyOrder("1", "2", "3");
            assertThat(received).isEmpty();
        }
    }

    @Test
    public void secondListenerOnAssignedTopicGetsOnlyLaterEvents() throws Exception {
        String topic = newTopic();
        try (EventClient client = newClient()) {
            BlockingQueue<Event> early = new LinkedBlockingQueue<>();
            client.subscribe(topic, early::add);
            for (int i = 0; i < 50; i++) {
                send(topic, "k", "old-" + i);
            }

            BlockingQueue<Event> late = new LinkedBlockingQueue<>();
            client.subscribe(topic, late::add);   // the topic is already assigned: no seek happens for it
            send(topic, "k", "new");

            assertThat(late.poll(WAIT.toMillis(), TimeUnit.MILLISECONDS)).extracting(Event::value).isEqualTo("new");
            assertThat(drainEvents(early, 51)).extracting(Event::value).hasSize(51).endsWith("new");
        }
    }

    @Test
    public void closedSubscriptionIsNeverCalledAgain() throws Exception {
        String topic = newTopic();
        try (EventClient client = newClient()) {
            List<Event> closed = new CopyOnWriteArrayList<>();
            BlockingQueue<Event> barrier = new LinkedBlockingQueue<>();
            client.subscribe(topic, barrier::add);

            EventSubscription subscription = new EventSubscription(client, topic, closed::add);
            send(topic, "1", "while subscribed");
            assertThat(barrier.poll(WAIT.toMillis(), TimeUnit.MILLISECONDS)).isNotNull();
            subscription.close();

            send(topic, "2", "after close");
            assertThat(barrier.poll(WAIT.toMillis(), TimeUnit.MILLISECONDS)).extracting(Event::key).isEqualTo("2");
            assertThat(closed).extracting(Event::key).containsExactly("1");
        }
    }

    @Test
    public void failingListenerDoesNotAffectAnother() throws Exception {
        String topic = newTopic();
        try (EventClient client = newClient()) {
            BlockingQueue<Event> healthy = new LinkedBlockingQueue<>();
            client.subscribe(topic, event -> {
                throw new IllegalStateException("listener bug");
            });
            client.subscribe(topic, healthy::add);

            send(topic, "1", "first");
            send(topic, "2", "second");   // the poll thread survived the first failure

            assertThat(drain(healthy, 2)).containsExactlyInAnyOrder("1", "2");
        }
    }

    @Test
    public void missingTopicFailsWithClearError() throws Exception {
        try (EventClient client = newClient()) {
            String topic = "no-such-topic-" + UUID.randomUUID();

            long begin = System.nanoTime();
            assertThatThrownBy(() -> client.subscribe(topic, event -> {
            }))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(topic);
            assertThat(Duration.ofNanos(System.nanoTime() - begin)).isLessThan(WAIT.plusSeconds(5));
            assertThat(admin.listTopics().names().get(WAIT.toMillis(), TimeUnit.MILLISECONDS))
                    .as("the client did not create it").doesNotContain(topic);
        }
    }

    @Test
    public void eventsFromEveryPartitionAreDelivered() throws Exception {
        String topic = newTopic();
        try (EventClient client = newClient()) {
            BlockingQueue<Event> received = new LinkedBlockingQueue<>();
            client.subscribe(topic, received::add);

            List<String> keys = IntStream.range(0, 30).mapToObj(String::valueOf).toList();
            for (String key : keys) {
                send(topic, key, "spread over partitions by key");
            }

            List<Event> events = drainEvents(received, keys.size());
            assertThat(events).extracting(Event::key).containsExactlyInAnyOrderElementsOf(keys);
            assertThat(events).extracting(Event::partition).contains(0, 1, 2);
        }
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    private static EventClient newClient() {
        return new EventClient(kafka.getBootstrapServers(), Map.of(), WAIT);
    }

    private static String newTopic() throws ExecutionException, InterruptedException, TimeoutException {
        String topic = "test-" + UUID.randomUUID();
        admin.createTopics(List.of(new NewTopic(topic, 3, (short) 1))).all().get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
        return topic;
    }

    /** Synchronous: when it returns, the record is on the broker. */
    private static void send(String topic, String key, String value) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
        record.headers().add("event-type", "test".getBytes());
        producer.send(record).get(WAIT.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static List<String> drain(BlockingQueue<Event> queue, int count) throws InterruptedException {
        return drainEvents(queue, count).stream().map(Event::key).toList();
    }

    /** Up to {@code count} events, each waited for at most {@link #WAIT}; fewer means some never came. */
    private static List<Event> drainEvents(BlockingQueue<Event> queue, int count) throws InterruptedException {
        List<Event> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Optional<Event> next = EventWaiter.pollMatching(queue, e -> true, WAIT);
            if (next.isEmpty()) {
                break;
            }
            events.add(next.get());
        }
        return events;
    }
}
