package com.study.principles.async.bad;

import com.study.principles.async.Event;
import com.study.principles.async.FakeBroker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * A typical first version of a test event listener. It works in a demo and fails in a suite:
 * <ul>
 *   <li><b>Listeners in a plain {@code ArrayList}</b> inside a {@code HashMap}, changed by test threads and
 *       iterated by the poll thread. A listener that unsubscribes during a dispatch throws
 *       {@code ConcurrentModificationException} (or, depending on its position, silently skips the next one).</li>
 *   <li><b>{@code subscribe} returns before reading is ready.</b> The start position is taken lazily on the
 *       poll thread's next round, like a Kafka {@code seekToEnd()} nobody forced with {@code position()}:
 *       an event published right after {@code subscribe} returns is behind the start and never delivered.</li>
 *   <li><b>No try/catch around a listener.</b> One failing listener kills the poll thread; every other
 *       subscription goes silent, and the next tests time out "for no reason".</li>
 *   <li><b>No way to know the thread is dead.</b> {@code subscribe} on a dead client "succeeds".</li>
 * </ul>
 */
public final class EventClient implements AutoCloseable {

    private static final long POLL_INTERVAL_MS = 10;

    private final FakeBroker broker;
    private final Map<String, List<Consumer<Event>>> listeners = new HashMap<>();
    private final Queue<String> pendingTopics = new ConcurrentLinkedQueue<>();
    private final Map<String, Long> positions = new HashMap<>();
    private final Thread pollThread;
    private volatile boolean running = true;

    public EventClient(FakeBroker broker) {
        this.broker = broker;
        this.pollThread = new Thread(this::pollLoop, "bad-event-client-poll");
        this.pollThread.setDaemon(true);
        this.pollThread.start();
    }

    /** Returns at once; the poll thread "will get to it". */
    public void subscribe(String topic, Consumer<Event> listener) {
        listeners.computeIfAbsent(topic, t -> new ArrayList<>()).add(listener);
        pendingTopics.add(topic);
    }

    public void unsubscribe(String topic, Consumer<Event> listener) {
        List<Consumer<Event>> topicListeners = listeners.get(topic);
        if (topicListeners != null) {
            topicListeners.remove(listener);
        }
    }

    @Override
    public void close() {
        running = false;
    }

    private void pollLoop() {
        while (running) {
            String topic;
            while ((topic = pendingTopics.poll()) != null) {
                positions.putIfAbsent(topic, broker.endOffset(topic));   // "seek to end", but only now
            }
            for (Map.Entry<String, Long> position : positions.entrySet()) {
                List<Event> events = broker.read(position.getKey(), position.getValue(), 500);
                for (Event event : events) {
                    for (Consumer<Event> listener : listeners.get(event.topic())) {
                        listener.accept(event);   // an exception here ends the thread
                    }
                }
                position.setValue(position.getValue() + events.size());
            }
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}
