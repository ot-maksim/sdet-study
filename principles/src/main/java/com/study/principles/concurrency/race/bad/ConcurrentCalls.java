package com.study.principles.concurrency.race.bad;

import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import java.util.stream.IntStream;

/**
 * A "run these calls concurrently" helper as it often appears in a test framework. It does run them on other
 * threads, and that is all it guarantees:
 * <ul>
 *   <li><b>The framework's shared executor.</b> Its size, not the test, decides how many calls overlap: with 2 threads,
 *       "10 concurrent withdrawals" are 5 rounds of 2; with 1, a sequential test.</li>
 *   <li><b>No start gate.</b> Calls start as the pool gets to them, staggered by thread start-up.</li>
 *   <li><b>{@code join()} in order, no timeout.</b> One hung call hangs the test; the first failed call throws and
 *       hides what the other calls returned.</li>
 *   <li>{@link #fireTogether}: a {@code CyclicBarrier} sized for the calls, on a pool smaller than that:
 *       the barrier waits for parties that cannot start. Forever: there is no timeout either.</li>
 * </ul>
 */
public final class ConcurrentCalls {

    private final ExecutorService pool;

    public ConcurrentCalls(ExecutorService pool) {
        this.pool = pool;
    }

    public <T> List<T> fire(int calls, Supplier<T> call) {
        List<CompletableFuture<T>> futures = IntStream.range(0, calls)
                .mapToObj(i -> CompletableFuture.supplyAsync(call, pool))
                .toList();
        return futures.stream().map(CompletableFuture::join).toList();
    }

    /** "Make them start at the same time." */
    public <T> List<T> fireTogether(int calls, Supplier<T> call) {
        CyclicBarrier barrier = new CyclicBarrier(calls);
        return fire(calls, () -> {
            try {
                barrier.await();
            } catch (InterruptedException | BrokenBarrierException e) {
                throw new CompletionException(e);
            }
            return call.get();
        });
    }
}
