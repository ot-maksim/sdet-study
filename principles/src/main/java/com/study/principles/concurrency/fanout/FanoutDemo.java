package com.study.principles.concurrency.fanout;

import com.study.principles.concurrency.fanout.good.Preconditions;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Four preconditions of 200 ms each, created three ways; then the same with one failing branch and with one stuck
 * dependency.
 *
 * <pre>
 * mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.concurrency.fanout.FanoutDemo
 * </pre>
 */
public final class FanoutDemo {

    private static final Duration LATENCY = Duration.ofMillis(200);

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            happyPath(pool);
            oneBranchFails(pool);
            stuckDependency(pool);
        } finally {
            pool.shutdownNow();
        }
    }

    private static void happyPath(ExecutorService pool) {
        FakeShopApi api = new FakeShopApi(LATENCY, false);
        long start = System.nanoTime();
        for (String type : Fixture.TYPES) {
            api.create(type);
        }
        print("sequential, 4 x 200 ms", "%d ms", millisSince(start));

        try (Cleanup cleanup = new Cleanup()) {
            start = System.nanoTime();
            new com.study.principles.concurrency.fanout.bad.Preconditions(api, cleanup).create();
            print("bad: supplyAsync on the common pool", "%d ms", millisSince(start));
        }
        try (Cleanup cleanup = new Cleanup()) {
            start = System.nanoTime();
            new Preconditions(api, pool, cleanup, Duration.ofSeconds(5)).create();
            print("good: own pool, deadline", "%d ms", millisSince(start));
        }
    }

    private static void oneBranchFails(ExecutorService pool) {
        FakeShopApi bad = new FakeShopApi(LATENCY, false);
        bad.failOn("promo", new IllegalStateException("500"));
        try (Cleanup cleanup = new Cleanup()) {
            new com.study.principles.concurrency.fanout.bad.Preconditions(bad, cleanup).create();
        } catch (RuntimeException expected) {
            // the test fails here
        }
        FakeShopApi good = new FakeShopApi(LATENCY, false);
        good.failOn("promo", new IllegalStateException("500"));
        try (Cleanup cleanup = new Cleanup()) {
            new Preconditions(good, pool, cleanup, Duration.ofSeconds(5)).create();
        } catch (RuntimeException expected) {
            // the test fails here
        }
        print("promo fails: entities left behind", "bad %d, good %d", bad.live().size(), good.live().size());
    }

    private static void stuckDependency(ExecutorService pool) {
        FakeShopApi bad = new FakeShopApi(LATENCY, false);
        bad.hangOn("stock", Duration.ofSeconds(2));
        long start = System.nanoTime();
        try (Cleanup cleanup = new Cleanup()) {
            new com.study.principles.concurrency.fanout.bad.Preconditions(bad, cleanup).create();
        }
        print("stock stuck for 2 s, bad", "returned after %d ms", millisSince(start));

        FakeShopApi good = new FakeShopApi(LATENCY, false);
        good.hangOn("stock", Duration.ofSeconds(2));
        start = System.nanoTime();
        try (Cleanup cleanup = new Cleanup()) {
            new Preconditions(good, pool, cleanup, Duration.ofMillis(500)).create();
        } catch (IllegalStateException e) {
            print("stock stuck for 2 s, good (deadline 500 ms)", "failed after %d ms: %s", millisSince(start), e.getMessage());
        }
    }

    private static void print(String name, String format, Object... args) {
        System.out.printf("%-44s %s%n", name, String.format(format, args));
    }

    private static long millisSince(long startNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
    }
}
