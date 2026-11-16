package com.study.principles.async;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

/** Small waiting helpers for the kata's tests: every wait has a deadline, none is a fixed pause. */
public final class Await {

    private Await() {
    }

    public static void latch(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Latch not released within 5 s");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** Waits until {@code count} reaches {@code expected}; fails after the timeout. */
    public static void count(IntSupplier count, int expected, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (count.getAsInt() < expected) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Expected " + expected + " within " + timeout + ", got " + count.getAsInt());
            }
            Thread.onSpinWait();
        }
    }

    /**
     * For the bad client only, whose {@code subscribe} returns before reading starts: publish pings until one
     * comes back, so the test knows the client is positioned. The good client never needs this.
     */
    public static void untilPositioned(FakeBroker broker, String topic, BlockingQueue<Event> received) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        try {
            while (System.nanoTime() < deadline) {
                broker.publish(topic, "ping", "{}");
                if (received.poll(20, TimeUnit.MILLISECONDS) != null) {
                    received.clear();
                    return;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        throw new AssertionError("Client never started reading " + topic);
    }
}
