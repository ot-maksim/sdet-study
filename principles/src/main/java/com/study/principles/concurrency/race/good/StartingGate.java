package com.study.principles.concurrency.race.good;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

/**
 * Runs N calls so that they really hit the SUT at the same moment, and reports what every one of them got.
 *
 * <pre>{@code
 * List<Outcome<Integer>> outcomes = StartingGate.runTogether(10, party -> wallet.withdraw("acc-1", 100), Duration.ofSeconds(5));
 * assertThat(outcomes).filteredOn(o -> Integer.valueOf(200).equals(o.value())).hasSize(1);   // the invariant, in the test thread
 * }</pre>
 *
 * How (the starting-gate harness from <i>Java Concurrency in Practice</i>, ch. 12):
 * <ol>
 *   <li>its <b>own pool of exactly N threads</b>: no shared executor decides the concurrency;</li>
 *   <li>every party counts down {@code ready} and waits on {@code start}; the harness opens {@code start} only when
 *       <b>all N are at the gate</b>, so thread start-up does not stagger the calls;</li>
 *   <li>every outcome is collected, value or exception, against <b>one deadline</b>: a failed call does not hide the
 *       others, a hung one is reported by number, the test never hangs;</li>
 *   <li>the pool is shut down (interrupted) whatever happens.</li>
 * </ol>
 * The calls return what the SUT answered; the test asserts on all of them together. No assertions inside a call.
 */
public final class StartingGate {

    private static final AtomicInteger poolIds = new AtomicInteger();

    /** What one party got: a value, or the exception its call threw (or a timeout). */
    public record Outcome<T>(int party, T value, Throwable error) {

        public boolean failed() {
            return error != null;
        }
    }

    private StartingGate() {
    }

    public static <T> List<Outcome<T>> runTogether(int parties, IntFunction<T> call, Duration timeout) {
        int poolId = poolIds.incrementAndGet();
        AtomicInteger threadIds = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(parties, r -> {
            Thread t = new Thread(r, "race-" + poolId + "-party-" + threadIds.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
        CountDownLatch ready = new CountDownLatch(parties);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int i = 0; i < parties; i++) {
                int party = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return call.apply(party);
                }));
            }
            if (!ready.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Only " + (parties - ready.getCount()) + " of " + parties
                        + " parties reached the gate within " + timeout);
            }
            start.countDown();
            return collect(futures, timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while running the race", e);
        } finally {
            pool.shutdownNow();
        }
    }

    private static <T> List<Outcome<T>> collect(List<Future<T>> futures, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        List<Outcome<T>> outcomes = new ArrayList<>();
        for (int party = 0; party < futures.size(); party++) {
            try {
                long left = Math.max(0, deadline - System.nanoTime());
                outcomes.add(new Outcome<>(party, futures.get(party).get(left, TimeUnit.NANOSECONDS), null));
            } catch (ExecutionException e) {
                outcomes.add(new Outcome<>(party, null, e.getCause()));
            } catch (TimeoutException e) {
                outcomes.add(new Outcome<>(party, null,
                        new TimeoutException("Party " + party + " did not finish within " + timeout)));
            }
        }
        return outcomes;
    }
}
