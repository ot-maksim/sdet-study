package com.study.principles.parallel.threadlocal;

import com.study.principles.parallel.Credentials;
import com.study.principles.parallel.FakeShop;
import com.study.principles.parallel.TwoThreads;
import com.study.principles.parallel.good.ShopApi;
import com.study.principles.parallel.good.ShopConfig;
import com.study.principles.parallel.FakeShopHttp;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ThreadLocal fixes the race between threads, and breaks where "thread" and "test" differ. */
class ThreadLocalDriverTest {

    private final FakeShop shop = new FakeShop();
    private final ShopApi api = new ShopApi(ShopConfig.DEFAULT, new FakeShopHttp(shop));
    private final String adminToken = api.login(FakeShop.ADMIN);

    @Test
    void fixesTheRace_betweenTestsOnDifferentThreads() {
        try (var threads = new TwoThreads()) {
            Credentials alice = newUser("alice");
            Credentials bob = newUser("bob");

            threads.onA(() -> openProfile(alice));
            threads.onB(() -> openProfile(bob));

            assertThat(threads.onA(() -> DriverManager.driver().findText("greeting"))).isEqualTo("Hello, alice");
            threads.onA(DriverManager::quit);
            threads.onB(DriverManager::quit);
        }
    }

    /** Awaitility, CompletableFuture, @Timeout(SEPARATE_THREAD): the same test, another thread. */
    @Test
    void breaks_whenTheTestItselfUsesAnotherThread() {
        try (var threads = new TwoThreads()) {
            threads.onA(() -> openProfile(newUser("carol")));

            assertThatThrownBy(() -> threads.onA(() ->
                    CompletableFuture.supplyAsync(() -> DriverManager.driver().findText("greeting")).join()))
                    .isInstanceOf(CompletionException.class)
                    .hasMessageContaining("no driver for thread");
            threads.onA(DriverManager::quit);
        }
    }

    /** Pool threads are reused: a missed quit() hands one test's logged-in browser to the next test. */
    @Test
    void leaks_intoTheNextTestOnTheSameThread_whenCleanupIsMissed() {
        try (var threads = new TwoThreads()) {
            threads.onA(() -> openProfile(newUser("dave")));     // test 1 forgets DriverManager.quit()

            // test 2 on the same pool thread; its base class "knows" the driver is already started
            threads.onA(() -> DriverManager.driver().get("/profile"));

            assertThat(threads.onA(() -> DriverManager.driver().findText("greeting"))).isEqualTo("Hello, dave");
            threads.onA(DriverManager::quit);
        }
    }

    private Credentials newUser(String name) {
        return api.as(adminToken).createUser(name);
    }

    private void openProfile(Credentials user) {
        DriverManager.start(shop);
        DriverManager.driver().addCookie("session", api.login(user));
        DriverManager.driver().get("/profile");
    }
}
