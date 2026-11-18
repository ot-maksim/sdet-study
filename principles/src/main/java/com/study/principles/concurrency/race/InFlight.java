package com.study.principles.concurrency.race;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Counts calls that are inside the SUT at the same time. The question a race test must be able to answer first:
 * <b>did my calls actually overlap?</b> If not, a green result says nothing about races.
 */
public final class InFlight {

    private final AtomicInteger current = new AtomicInteger();
    private final AtomicInteger max = new AtomicInteger();

    public void enter() {
        int now = current.incrementAndGet();
        max.accumulateAndGet(now, Math::max);
    }

    public void exit() {
        current.decrementAndGet();
    }

    public int max() {
        return max.get();
    }

    /** The time a request spends between reading and writing: a DB round trip, a call to another service. */
    static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
