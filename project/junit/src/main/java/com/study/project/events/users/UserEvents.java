package com.study.project.events.users;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.study.project.api.users.User;
import com.study.project.events.Event;
import com.study.project.events.EventClient;
import com.study.project.events.EventSubscription;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * The SUT's {@code users} topic as a test sees it — what {@code UserApi} is for {@code /api/users}: the contract
 * (topic, key = user id, header {@code event-type}, value = the user as JSON) is known here and nowhere else.
 *
 * <p>Test-scoped: subscribed when the test's parameters are resolved, so before the test body, and closed after
 * the test. Events of earlier tests are never seen; events of parallel tests are — they are skipped by id.
 *
 * <p>Unlike {@code EventWaiter.pollMatching}, nothing is dropped: an event that does not match the current wait is
 * kept, because the test may wait for it next (create Ada, create Bob, check Bob, then Ada).
 * Used by the test's own thread only; the poll thread just adds to the queue.
 */
public final class UserEvents implements AutoCloseable {

    public static final String TOPIC = "users";
    public static final String TYPE_HEADER = "event-type";
    public static final String CREATED = "user.created";

    private static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            // Tolerant reader, as the API client: a new field in the event must not break unrelated tests
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final BlockingQueue<Event> arriving = new LinkedBlockingQueue<>();
    private final List<Event> seen = new ArrayList<>();
    private final Duration timeout;
    private final EventSubscription subscription;

    public UserEvents(EventClient client, Duration timeout) {
        this.timeout = timeout;
        this.subscription = new EventSubscription(client, TOPIC, arriving::add);
    }

    /** The user announced by {@code user.created} for this id; empty if none came within the timeout. */
    public Optional<User> created(long userId) {
        return await(event -> String.valueOf(userId).equals(event.key()) && CREATED.equals(event.header(TYPE_HEADER)))
                .map(UserEvents::user);
    }

    /** Every event of this user seen so far, in arrival order; waits for nothing. */
    public List<Event> seenFor(long userId) {
        arriving.drainTo(seen);
        return seen.stream().filter(e -> String.valueOf(userId).equals(e.key())).toList();
    }

    @Override
    public void close() {
        subscription.close();
    }

    private Optional<Event> await(Predicate<Event> matcher) {
        arriving.drainTo(seen);
        Optional<Event> already = seen.stream().filter(matcher).findFirst();
        if (already.isPresent()) {
            return already;
        }
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            while (true) {
                long left = deadline - System.nanoTime();
                if (left <= 0) {
                    return Optional.empty();
                }
                Event event = arriving.poll(left, TimeUnit.NANOSECONDS);
                if (event != null) {
                    seen.add(event);   // kept even when it is not the one: the test may ask for it next
                    if (matcher.test(event)) {
                        return Optional.of(event);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a user event", e);
        }
    }

    private static User user(Event event) {
        try {
            return JSON.readValue(event.value(), User.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Not a user in " + event, e);
        }
    }

    @Override
    public String toString() {
        return "UserEvents(" + TOPIC + ", timeout " + timeout + ")";
    }
}
