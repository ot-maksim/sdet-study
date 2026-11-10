package com.study.principles.parallel;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Two "test threads" with a scripted interleaving: each step runs on its own thread, but the order of
 * steps is fixed by the calling test. That turns a race that happens "sometimes" in a parallel suite
 * into a deterministic, readable scenario:
 *
 * <pre>
 * threads.onA(() -> testA.login(alice));
 * threads.onB(() -> testB.login(bob));
 * threads.onA(testA::whoAmI);   // what A sees after B ran in between
 * </pre>
 *
 * Real threads matter: per-thread solutions ({@code ThreadLocal}) behave differently on one thread.
 */
public final class TwoThreads implements AutoCloseable {

    private final ExecutorService a = Executors.newSingleThreadExecutor(r -> new Thread(r, "test-A"));
    private final ExecutorService b = Executors.newSingleThreadExecutor(r -> new Thread(r, "test-B"));

    public <T> T onA(Callable<T> step) {
        return run(a, step);
    }

    public void onA(Runnable step) {
        run(a, () -> {
            step.run();
            return null;
        });
    }

    public <T> T onB(Callable<T> step) {
        return run(b, step);
    }

    public void onB(Runnable step) {
        run(b, () -> {
            step.run();
            return null;
        });
    }

    private static <T> T run(ExecutorService thread, Callable<T> step) {
        try {
            return thread.submit(step).get();
        } catch (ExecutionException e) {
            // rethrow what the step threw, so assertions and exceptions read as if called directly
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (e.getCause() instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException(e.getCause());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() {
        a.shutdownNow();
        b.shutdownNow();
    }
}
