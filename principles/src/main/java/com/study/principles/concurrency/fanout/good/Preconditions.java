package com.study.principles.concurrency.fanout.good;

import com.study.principles.concurrency.fanout.Cleanup;
import com.study.principles.concurrency.fanout.FakeShopApi;
import com.study.principles.concurrency.fanout.Fixture;
import com.study.principles.concurrency.fanout.TestContext;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Parallel preconditions that behave on the unhappy paths too.
 *
 * <pre>{@code
 * Fixture f = new Preconditions(api, pool, cleanup, Duration.ofSeconds(10)).create();
 * }</pre>
 *
 * <ul>
 *   <li><b>The executor comes from outside</b>, owned by the test lifecycle (an extension creates it, shuts it down
 *       with {@code shutdownNow()} after the test, which also interrupts a branch the test gave up on).</li>
 *   <li><b>The test's context is carried</b> into every branch with {@link TestContext#wrap}.</li>
 *   <li><b>Each branch registers its own cleanup</b> the moment its entity exists: a failure elsewhere does not leak it.</li>
 *   <li><b>One deadline for the whole fan-out.</b> On timeout the error names the branches still pending; on failure, it
 *       lists <b>every</b> failed branch, not just the first.</li>
 * </ul>
 * {@code CompletableFuture.cancel(true)} does <b>not</b> interrupt a running {@code supplyAsync} task: only the pool's
 * owner can stop it. That is why the executor must be the test's, not the JVM's.
 */
public final class Preconditions {

    private final FakeShopApi api;
    private final Executor pool;
    private final Cleanup cleanup;
    private final Duration timeout;

    public Preconditions(FakeShopApi api, Executor pool, Cleanup cleanup, Duration timeout) {
        this.api = api;
        this.pool = pool;
        this.cleanup = cleanup;
        this.timeout = timeout;
    }

    public Fixture create() {
        Map<String, CompletableFuture<String>> branches = new LinkedHashMap<>();
        for (String type : Fixture.TYPES) {
            branches.put(type, CompletableFuture.supplyAsync(TestContext.wrap(() -> {
                String id = api.create(type);
                cleanup.register(() -> api.delete(type, id));   // right away, from the branch
                return id;
            }), pool));
        }
        awaitAll(branches);
        return new Fixture(branches.get("user").join(), branches.get("product").join(),
                branches.get("stock").join(), branches.get("promo").join());
    }

    private void awaitAll(Map<String, CompletableFuture<String>> branches) {
        CompletableFuture<Void> all = CompletableFuture.allOf(branches.values().toArray(CompletableFuture[]::new));
        try {
            all.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while creating preconditions", e);
        } catch (TimeoutException e) {
            List<String> pending = branches.entrySet().stream().filter(b -> !b.getValue().isDone()).map(Map.Entry::getKey).toList();
            throw new IllegalStateException("Preconditions not ready within " + timeout + ": still waiting for " + pending);
        } catch (ExecutionException e) {
            throw allFailures(branches);
        }
    }

    /** {@code allOf} fails with the first failure only; the test deserves all of them. */
    private static IllegalStateException allFailures(Map<String, CompletableFuture<String>> branches) {
        List<String> failed = new ArrayList<>();
        List<Throwable> causes = new ArrayList<>();
        branches.forEach((type, branch) -> {
            if (branch.isCompletedExceptionally()) {
                Throwable cause = unwrap(branch.handle((value, error) -> error).join());
                failed.add(type + " (" + cause.getMessage() + ")");
                causes.add(cause);
            }
        });
        IllegalStateException result = new IllegalStateException("Preconditions failed: " + failed);
        causes.forEach(result::addSuppressed);
        return result;
    }

    /** {@code supplyAsync} stores a task's exception wrapped in a {@code CompletionException}. */
    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }
}
