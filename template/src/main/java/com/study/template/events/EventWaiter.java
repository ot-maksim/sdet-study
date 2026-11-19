package com.study.template.events;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Waits for the event a test is about, skipping the noise in the topic — moved unchanged from the kata.
 * Unlike a single {@code queue.poll(timeout)}, it does not fail when somebody else's event comes first
 * (a parallel test creating its own user on the same topic).
 */
public final class EventWaiter {

    private EventWaiter() {
    }

    /**
     * Takes events from the queue until one matches or the timeout runs out; non-matching events are dropped.
     * Returns as soon as the match arrives: the timeout is an upper bound, not a pause.
     */
    public static <T> Optional<T> pollMatching(BlockingQueue<T> queue, Predicate<? super T> matcher, Duration timeout)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            long left = deadline - System.nanoTime();
            if (left <= 0) {
                return Optional.empty();
            }
            T item = queue.poll(left, TimeUnit.NANOSECONDS);
            if (item != null && matcher.test(item)) {
                return Optional.of(item);
            }
        }
    }
}
