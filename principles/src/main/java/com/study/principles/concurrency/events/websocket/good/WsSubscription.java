package com.study.principles.concurrency.events.websocket.good;

import com.study.principles.concurrency.events.EventWaiter;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;

/**
 * One channel of a {@link WsEventClient}. It owns its queue, so there is nothing a late message could spoil:
 * after {@link #close()} a message for this channel is dropped by the client, and no
 * {@code SwitchableListener} is needed.
 *
 * <pre>{@code
 * try (var orders = client.subscribe("orders")) {          // returns after the server's ack
 *     sut.pay(id);
 *     Optional<String> paid = orders.await(m -> id.equals(Messages.field(m, "orderId")), Duration.ofSeconds(5));
 * }
 * }</pre>
 */
public final class WsSubscription implements AutoCloseable {

    /** A message, or the "connection lost" pill that wakes a waiting test at once. */
    private record Inbound(String text, String lostReason) {
    }

    private final WsEventClient client;
    private final String channel;
    private final BlockingQueue<Inbound> queue = new LinkedBlockingQueue<>();

    WsSubscription(WsEventClient client, String channel) {
        this.client = client;
        this.channel = channel;
    }

    public String channel() {
        return channel;
    }

    /**
     * Waits for the first matching message, skipping the others. Returns empty after the timeout.
     *
     * @throws ConnectionLostException as soon as the connection closes: no point waiting for a push that cannot come
     */
    public Optional<String> await(Predicate<String> matcher, Duration timeout) throws InterruptedException {
        Optional<Inbound> got = EventWaiter.pollMatching(
                queue, in -> in.lostReason() != null || matcher.test(in.text()), timeout);
        if (got.isPresent() && got.get().lostReason() != null) {
            queue.add(got.get());   // keep the pill for the next await
            throw new ConnectionLostException(got.get().lostReason(), channel);
        }
        return got.map(Inbound::text);
    }

    /** Called on the io thread. */
    void offer(String message) {
        queue.add(new Inbound(message, null));
    }

    /** Called on the io thread. A pill in the queue, not a flag to poll: the waiting test wakes up now. */
    void connectionLost(String reason) {
        queue.add(new Inbound(null, reason));
    }

    @Override
    public void close() {
        client.unsubscribe(channel);
    }
}
