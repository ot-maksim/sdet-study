package com.study.principles.concurrency;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;

/** Small waiting helpers for the concurrency katas' tests: every wait has a deadline, none is a fixed pause. */
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
}
