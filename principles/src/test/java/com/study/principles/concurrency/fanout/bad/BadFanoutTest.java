package com.study.principles.concurrency.fanout.bad;

import com.study.principles.concurrency.fanout.Cleanup;
import com.study.principles.concurrency.fanout.FakeShopApi;
import com.study.principles.concurrency.fanout.FakeShopApi.Entity;
import com.study.principles.concurrency.fanout.TestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Characterization tests: they pass <i>because</i> they pin the problems of {@link Preconditions}. */
class BadFanoutTest {

    private final Cleanup cleanup = new Cleanup();

    @AfterEach
    void tearDown() {
        TestContext.clear();
    }

    @Test
    void theTestsContextStaysOnTheTestThread_everyBranchGets401() {
        FakeShopApi api = new FakeShopApi(Duration.ofMillis(50), true);
        TestContext.setToken("token-of-this-test");   // set by the auth extension, on the test thread

        assertThatThrownBy(() -> new Preconditions(api, cleanup).create())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(FakeShopApi.Unauthorized.class);
    }

    @Test
    void oneBranchFails_whatTheOthersCreatedIsNeverCleanedUp() {
        FakeShopApi api = new FakeShopApi(Duration.ofMillis(50), false);
        api.failOn("promo", new IllegalStateException("500 Internal Server Error"));

        assertThatThrownBy(() -> new Preconditions(api, cleanup).create()).isInstanceOf(CompletionException.class);
        cleanup.close();

        assertThat(api.live()).extracting(Entity::type).containsExactlyInAnyOrder("user", "product", "stock");
    }

    @Test
    void stuckDependency_theTestWaitsAsLongAsItIsStuck() {
        FakeShopApi api = new FakeShopApi(Duration.ofMillis(50), false);
        api.hangOn("stock", Duration.ofSeconds(1));
        long start = System.nanoTime();

        new Preconditions(api, cleanup).create();

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isGreaterThanOrEqualTo(Duration.ofSeconds(1));
        cleanup.close();
    }
}
