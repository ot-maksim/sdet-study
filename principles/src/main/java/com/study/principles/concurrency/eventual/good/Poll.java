package com.study.principles.concurrency.eventual.good;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A hand-rolled poll done right, in ~80 lines: what Awaitility gives you, and what to check in any home-grown one.
 *
 * <pre>{@code
 * OrderView paid = Poll.of("order-1 in the read model", () -> system.getOrder("order-1"))
 *         .ignoring(ReadErrors.NotFound.class)          // "not yet": expected
 *         .every(Duration.ofMillis(100))
 *         .atMost(Duration.ofSeconds(5))
 *         .until(o -> o.status().isAtLeast(PAID));       // a condition that stays true
 * assertThat(paid.customer()).isEqualTo("alice");       // assert on the value the poll saw, not on a re-read
 * }</pre>
 *
 * <ul>
 *   <li><b>A deadline</b>, always; the interval is a pause between reads, not the precision of the check.</li>
 *   <li><b>Only named exceptions are "not yet".</b> Anything else (401, 500, a bug in the test) fails at once, not after
 *       the timeout.</li>
 *   <li><b>Returns the value that matched</b>, so the test asserts on what the poll saw.</li>
 *   <li><b>The failure says what it waited for, how long, how many reads, and what it saw last</b> (value or error).</li>
 *   <li>Runs on the caller's thread: thread-bound clients keep working.</li>
 * </ul>
 */
public final class Poll<T> {

    private final String description;
    private final Supplier<T> read;
    private final Class<? extends Throwable> notYet;
    private final Duration interval;
    private final Duration timeout;

    private Poll(String description, Supplier<T> read, Class<? extends Throwable> notYet, Duration interval, Duration timeout) {
        this.description = description;
        this.read = read;
        this.notYet = notYet;
        this.interval = interval;
        this.timeout = timeout;
    }

    public static <T> Poll<T> of(String description, Supplier<T> read) {
        return new Poll<>(description, read, null, Duration.ofMillis(100), Duration.ofSeconds(5));
    }

    /** Exceptions of this type mean "not there yet" and are retried; all others fail the poll at once. */
    public Poll<T> ignoring(Class<? extends Throwable> notYet) {
        return new Poll<>(description, read, notYet, interval, timeout);
    }

    public Poll<T> every(Duration interval) {
        return new Poll<>(description, read, notYet, interval, timeout);
    }

    public Poll<T> atMost(Duration timeout) {
        return new Poll<>(description, read, notYet, interval, timeout);
    }

    /** Reads until the value matches; returns that value. */
    public T until(Predicate<? super T> condition) {
        Objects.requireNonNull(condition, "condition");
        long start = System.nanoTime();
        long deadline = start + timeout.toNanos();
        int attempts = 0;
        Object last = "nothing yet";
        while (true) {
            attempts++;
            try {
                T value = read.get();
                if (condition.test(value)) {
                    return value;
                }
                last = value;
            } catch (RuntimeException e) {
                if (notYet == null || !notYet.isInstance(e)) {
                    throw e;   // not a "not yet": waiting longer will not help
                }
                last = e;
            }
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                throw new AssertionError(description + ": not within " + timeout + " (" + attempts
                        + " reads); last " + (last instanceof Throwable ? "error: " + last : "value: " + last));
            }
            sleep(Math.min(left, interval.toNanos()));
        }
    }

    private static void sleep(long nanos) {
        try {
            Thread.sleep(nanos / 1_000_000, (int) (nanos % 1_000_000));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while polling", e);
        }
    }
}
