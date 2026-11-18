package com.study.principles.concurrency.fanout.bad;

import com.study.principles.concurrency.fanout.Cleanup;
import com.study.principles.concurrency.fanout.FakeShopApi;
import com.study.principles.concurrency.fanout.Fixture;

import java.util.concurrent.CompletableFuture;

/**
 * "Create the preconditions in parallel", as it usually first appears. It is faster than sequential and correct on
 * the happy path. Off it:
 * <ul>
 *   <li><b>No executor</b>: {@code supplyAsync} runs on the common {@code ForkJoinPool}, sized by the machine and shared
 *       with every parallel stream and every other test in the JVM. The test does not own it, so it cannot stop it.</li>
 *   <li><b>The test's {@code ThreadLocal} context stays behind</b>: the auth token set for this test is not there on the
 *       pool thread, and every branch gets 401.</li>
 *   <li><b>Cleanup is registered after everything succeeded</b>: if one branch fails, the entities the other branches
 *       created are never deleted.</li>
 *   <li><b>{@code join()} without a timeout</b>: one stuck dependency stalls the test for as long as it is stuck.</li>
 * </ul>
 */
public final class Preconditions {

    private final FakeShopApi api;
    private final Cleanup cleanup;

    public Preconditions(FakeShopApi api, Cleanup cleanup) {
        this.api = api;
        this.cleanup = cleanup;
    }

    public Fixture create() {
        CompletableFuture<String> user = CompletableFuture.supplyAsync(() -> api.create("user"));
        CompletableFuture<String> product = CompletableFuture.supplyAsync(() -> api.create("product"));
        CompletableFuture<String> stock = CompletableFuture.supplyAsync(() -> api.create("stock"));
        CompletableFuture<String> promo = CompletableFuture.supplyAsync(() -> api.create("promo"));

        CompletableFuture.allOf(user, product, stock, promo).join();

        Fixture fixture = new Fixture(user.join(), product.join(), stock.join(), promo.join());
        cleanup.register(() -> api.delete("user", fixture.user()));
        cleanup.register(() -> api.delete("product", fixture.product()));
        cleanup.register(() -> api.delete("stock", fixture.stock()));
        cleanup.register(() -> api.delete("promo", fixture.promo()));
        return fixture;
    }
}
