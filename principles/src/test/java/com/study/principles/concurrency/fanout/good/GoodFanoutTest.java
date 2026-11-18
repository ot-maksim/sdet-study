package com.study.principles.concurrency.fanout.good;

import com.study.principles.concurrency.Await;
import com.study.principles.concurrency.fanout.Cleanup;
import com.study.principles.concurrency.fanout.FakeShopApi;
import com.study.principles.concurrency.fanout.Fixture;
import com.study.principles.concurrency.fanout.TestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The scenarios of {@code bad.BadFanoutTest}, done right. The pool and the cleanup are the test's, closed after it. */
class GoodFanoutTest {

    private static final AtomicInteger threadIds = new AtomicInteger();

    private final ExecutorService pool = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "fanout-" + threadIds.incrementAndGet());
        t.setDaemon(true);
        return t;
    });
    private final Cleanup cleanup = new Cleanup();

    @AfterEach
    void tearDown() {
        cleanup.close();
        pool.shutdownNow();   // interrupts a branch the test gave up on
        TestContext.clear();
    }

    private Preconditions preconditions(FakeShopApi api, Duration timeout) {
        return new Preconditions(api, pool, cleanup, timeout);
    }

    // --- 1. Parallel, with the test's context ---------------------------------------------------------------

    @Test
    void fourIndependentBranches_runAtOnce() {
        FakeShopApi api = new FakeShopApi(Duration.ofMillis(200), false);
        long start = System.nanoTime();

        Fixture fixture = preconditions(api, Duration.ofSeconds(5)).create();

        assertThat(fixture.user()).startsWith("user-");
        assertThat(api.maxInFlight()).isEqualTo(4);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).as("4 x 200 ms sequentially").isLessThan(Duration.ofMillis(600));
    }

    @Test
    void theTestsContextIsCarriedIntoEveryBranch() {
        FakeShopApi api = new FakeShopApi(Duration.ofMillis(50), true);
        TestContext.setToken("token-of-this-test");

        Fixture fixture = preconditions(api, Duration.ofSeconds(5)).create();

        assertThat(fixture.promo()).startsWith("promo-");
    }

    @Test
    void theContextDoesNotLeakIntoTheNextTaskOnAReusedPoolThread() {
        FakeShopApi api = new FakeShopApi(Duration.ofMillis(10), true);
        TestContext.setToken("token-of-this-test");
        preconditions(api, Duration.ofSeconds(5)).create();

        for (int i = 0; i < 8; i++) {   // more tasks than threads: every thread is reused
            assertThat(CompletableFuture.supplyAsync(TestContext::token, pool).join()).isNull();
        }
    }

    // --- 2. Failures and cleanup ----------------------------------------------------------------------------

    @Test
    void failedBranches_areAllReported_andEverythingCreatedIsCleanedUp() {
        FakeShopApi api = new FakeShopApi(Duration.ofMillis(50), false);
        api.failOn("stock", new IllegalStateException("503 Warehouse unavailable"));
        api.failOn("promo", new IllegalStateException("500 Internal Server Error"));

        assertThatThrownBy(() -> preconditions(api, Duration.ofSeconds(5)).create())
                .hasMessage("Preconditions failed: [stock (503 Warehouse unavailable), promo (500 Internal Server Error)]")
                .satisfies(e -> assertThat(e.getSuppressed()).hasSize(2));
        cleanup.close();

        assertThat(api.live()).isEmpty();
    }

    @Test
    void stuckDependency_failsAtTheDeadline_namingIt() {
        FakeShopApi api = new FakeShopApi(Duration.ofMillis(50), false);
        api.hangOn("stock", Duration.ofSeconds(2));
        long start = System.nanoTime();

        assertThatThrownBy(() -> preconditions(api, Duration.ofMillis(300)).create())
                .hasMessage("Preconditions not ready within PT0.3S: still waiting for [stock]");
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void aBranchThatFinishesAfterTheTestGaveUp_isStillCleanedUp() {
        FakeShopApi api = new FakeShopApi(Duration.ofMillis(20), false);
        api.hangOn("stock", Duration.ofMillis(300));

        assertThatThrownBy(() -> preconditions(api, Duration.ofMillis(100)).create()).isInstanceOf(IllegalStateException.class);
        cleanup.close();                                   // the test is over; stock is still being created

        Await.until(() -> api.live().isEmpty(), Duration.ofSeconds(2));   // registered late, undone at once
    }
}
