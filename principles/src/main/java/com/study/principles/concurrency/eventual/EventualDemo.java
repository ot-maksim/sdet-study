package com.study.principles.concurrency.eventual;

import com.study.principles.concurrency.eventual.good.OrderChecks;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * One check, "a placed order shows up in the read model", written four ways and run 20 times each.
 * The projection lag varies from 50 to 400 ms, as it does on a shared stand.
 *
 * <pre>
 * mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.concurrency.eventual.EventualDemo
 * </pre>
 */
public final class EventualDemo {

    private static final int RUNS = 20;

    public static void main(String[] args) {
        run("assert right after the write", system -> {
            String id = system.placeOrder("alice");
            return system.find(id).isPresent();
        });
        run("sleep(200), then read", system -> {
            String id = system.placeOrder("alice");
            sleep(200);
            return system.find(id).isPresent();
        });
        run("sleep(500), then read", system -> {
            String id = system.placeOrder("alice");
            sleep(500);
            return system.find(id).isPresent();
        });
        run("Poll: every 50 ms, at most 5 s", system -> {
            String id = system.placeOrder("alice");
            new OrderChecks(system, Duration.ofSeconds(5)).orderVisible(id);
            return true;
        });
    }

    private interface Check {
        boolean passed(FakeOrderSystem system);
    }

    private static void run(String name, Check check) {
        int ok = 0;
        long totalNanos = 0;
        int totalReads = 0;
        for (int i = 0; i < RUNS; i++) {
            try (var system = new FakeOrderSystem(
                    () -> Duration.ofMillis(ThreadLocalRandom.current().nextLong(50, 400)), Duration.ZERO)) {
                long start = System.nanoTime();
                ok += check.passed(system) ? 1 : 0;
                totalNanos += System.nanoTime() - start;
                totalReads += system.reads();
            }
        }
        System.out.printf("%-34s %2d/%d ok, avg %4d ms, %4.1f reads per test%n", name, ok, RUNS,
                TimeUnit.NANOSECONDS.toMillis(totalNanos / RUNS), (double) totalReads / RUNS);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
