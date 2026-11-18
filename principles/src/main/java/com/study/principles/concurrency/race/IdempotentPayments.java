package com.study.principles.concurrency.race;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The fix: <b>claim the key atomically before doing the work</b> ({@code putIfAbsent} of an in-flight marker; in SQL an
 * {@code INSERT} into a table with a unique key). A request that finds the key claimed waits for the first one's result
 * and returns the same charge. (Stripe answers such a request with 409 "in progress" instead; either way, money is
 * taken once.)
 */
public final class IdempotentPayments implements Payments {

    private final Map<String, CompletableFuture<Charge>> byKey = new ConcurrentHashMap<>();
    private final AtomicInteger charges = new AtomicInteger();
    private final long windowMillis;

    public IdempotentPayments(long windowMillis) {
        this.windowMillis = windowMillis;
    }

    @Override
    public Charge charge(String idempotencyKey, long amount) {
        CompletableFuture<Charge> mine = new CompletableFuture<>();
        CompletableFuture<Charge> claimed = byKey.putIfAbsent(idempotencyKey, mine);
        if (claimed != null) {
            return await(claimed);
        }
        try {
            InFlight.pause(windowMillis);
            Charge charge = new Charge("ch_" + charges.incrementAndGet(), idempotencyKey, amount);
            mine.complete(charge);
            return charge;
        } catch (RuntimeException e) {
            byKey.remove(idempotencyKey, mine);   // a failed attempt must not block the key forever
            mine.completeExceptionally(e);
            throw e;
        }
    }

    @Override
    public int chargesMade() {
        return charges.get();
    }

    private static Charge await(CompletableFuture<Charge> inProgress) {
        try {
            return inProgress.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("The first request with this key failed or hangs", e);
        }
    }
}
