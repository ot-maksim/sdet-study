package com.study.principles.parallel.good;

import com.study.principles.parallel.Credentials;
import com.study.principles.parallel.FakeWebDriver;
import com.study.principles.parallel.TwoThreads;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The same interleavings as {@code BadSharedStateTest}, wired by hand the way {@link ShopExtension} wires
 * them: run-wide {@link ShopEnvironment}, per-test driver and {@link TestUsers}. Then the shared state that
 * cannot be avoided (a scarce account, run-wide counters and registries) under real concurrency.
 */
class GoodSharedStateTest {

    private final ShopEnvironment environment = ShopEnvironment.connect(ShopConfig.DEFAULT);
    private final ShopApi api = environment.api();

    @AfterEach
    void endOfRun() {
        environment.close();
    }

    // ---- the bad scenarios, now isolated --------------------------------------------------------

    @Test
    void immutableClient_eachTestKeepsItsOwnAuth() {
        try (var threads = new TwoThreads()) {
            Credentials alice = threads.onA(() -> environment.newTestUsers().create());
            Credentials bob = threads.onB(() -> environment.newTestUsers().create());

            ShopApi asAlice = threads.onA(() -> api.as(api.login(alice)));
            ShopApi asBob = threads.onB(() -> api.as(api.login(bob)));

            assertThat(threads.onA(() -> asAlice.me().username())).isEqualTo(alice.username());
            assertThat(threads.onB(() -> asBob.me().username())).isEqualTo(bob.username());
        }
    }

    @Test
    void immutableClient_nothingLeaksIntoLaterTests() {
        Credentials alice = environment.newTestUsers().create();
        ShopApi german = api.as(api.login(alice)).withLanguage("de");
        assertThat(german.me().greeting()).isEqualTo("Hallo, " + alice.username());

        assertThat(api.meStatus()).isEqualTo(401);                              // the shared one is still anonymous
        assertThat(api.as(api.login(alice)).me().greeting()).isEqualTo("Hello, " + alice.username());
    }

    @Test
    void driverPerTest_eachTestSeesItsOwnSession() {
        try (var threads = new TwoThreads();
             FakeWebDriver driverA = environment.newDriver();
             FakeWebDriver driverB = environment.newDriver()) {
            Credentials alice = threads.onA(() -> environment.newTestUsers().create());
            Credentials bob = threads.onB(() -> environment.newTestUsers().create());

            threads.onA(() -> openProfile(driverA, alice));
            threads.onB(() -> openProfile(driverB, bob));

            assertThat(threads.onA(() -> driverA.findText("greeting"))).isEqualTo("Hello, " + alice.username());
            assertThat(threads.onB(() -> driverB.findText("greeting"))).isEqualTo("Hello, " + bob.username());
        }
    }

    @Test
    void driverPerTest_quitAffectsOnlyItsOwner() {
        try (FakeWebDriver driverA = environment.newDriver(); FakeWebDriver driverB = environment.newDriver()) {
            openProfile(driverA, environment.newTestUsers().create());

            driverB.quit();

            assertThat(driverA.getCurrentUrl()).isEqualTo("/profile");
        }
    }

    /** The good counterpart of ThreadLocalDriverTest: an object passed explicitly works on any thread. */
    @Test
    void explicitlyPassedDriver_worksOnAnyThreadOfTheTest() {
        try (FakeWebDriver driver = environment.newDriver()) {
            Credentials alice = environment.newTestUsers().create();
            openProfile(driver, alice);

            // what Awaitility or CompletableFuture do: the lambda captures this test's driver, not "the thread's"
            String greeting = CompletableFuture.supplyAsync(() -> driver.findText("greeting")).join();

            assertThat(greeting).isEqualTo("Hello, " + alice.username());
        }
    }

    // ---- shared state that cannot be avoided ----------------------------------------------------

    /** A global switch of the SUT: per-test clients and browsers do not help, the state lives in the SUT. */
    @Test
    void globalFeatureFlag_isSharedStateOfTheSut_evenWithIsolatedClients() {
        try (var threads = new TwoThreads();
             FakeWebDriver driverA = environment.newDriver();
             FakeWebDriver driverB = environment.newDriver()) {
            Credentials alice = threads.onA(() -> environment.newTestUsers().create());
            Credentials bob = threads.onB(() -> environment.newTestUsers().create());

            threads.onA(() -> environment.admin().setFeature("new-profile", true));   // test A: "new page works"
            threads.onB(() -> environment.admin().setFeature("new-profile", false));  // test B: "old page works"

            threads.onA(() -> openProfile(driverA, alice));
            assertThat(threads.onA(() -> driverA.findText("title"))).isEqualTo("Profile");  // A expected "Your profile"
        }
    }

    /** Best fix when the SUT allows it: move the switch from global to the test's own data. No lock needed. */
    @Test
    void perUserFeatureOverride_isolatesByData() {
        try (var threads = new TwoThreads();
             FakeWebDriver driverA = environment.newDriver();
             FakeWebDriver driverB = environment.newDriver()) {
            Credentials alice = threads.onA(() -> environment.newTestUsers().create());
            Credentials bob = threads.onB(() -> environment.newTestUsers().create());

            threads.onA(() -> environment.admin().setFeatureFor("new-profile", alice.username(), true));
            threads.onB(() -> environment.admin().setFeatureFor("new-profile", bob.username(), false));

            threads.onA(() -> openProfile(driverA, alice));
            threads.onB(() -> openProfile(driverB, bob));
            assertThat(threads.onA(() -> driverA.findText("title"))).isEqualTo("Your profile");
            assertThat(threads.onB(() -> driverB.findText("title"))).isEqualTo("Profile");
        }
    }

    /** Isolated clients are not enough when the <i>data</i> is shared: same account, two sessions. */
    @Test
    void sameScarceAccountInTwoTests_kicksOneOut_evenWithIsolatedClients() {
        Credentials premium = ShopConfig.DEFAULT.premiumAccounts().get(0);
        try (FakeWebDriver driverA = environment.newDriver(); FakeWebDriver driverB = environment.newDriver()) {
            openProfile(driverA, premium);
            openProfile(driverB, premium);   // the shop ends A's session

            driverA.get("/profile");
            assertThat(driverA.getCurrentUrl()).isEqualTo("/login");
        }
    }

    @Test
    void pool_givesParallelTestsDifferentAccounts_andTakesThemBackOnClose() {
        try (var threads = new TwoThreads();
             TestUsers usersA = environment.newTestUsers();
             TestUsers usersB = environment.newTestUsers();
             FakeWebDriver driverA = environment.newDriver();
             FakeWebDriver driverB = environment.newDriver()) {
            Credentials a = threads.onA(usersA::leasePremium);
            Credentials b = threads.onB(usersB::leasePremium);
            assertThat(a).isNotEqualTo(b);

            threads.onA(() -> openProfile(driverA, a));
            threads.onB(() -> openProfile(driverB, b));
            assertThat(threads.onA(() -> driverA.findText("plan"))).isEqualTo("PREMIUM");

            usersA.close();
            assertThat(environment.premiumAccounts().available()).isEqualTo(1);
        }
    }

    @Test
    void pool_whenAllAccountsAreLeased_failsWithAClearMessageInsteadOfSharing() {
        var config = new ShopConfig(ShopConfig.DEFAULT.baseUri(), ShopConfig.DEFAULT.admin(),
                ShopConfig.DEFAULT.premiumAccounts(), "en", Duration.ofMillis(50));
        try (ShopEnvironment small = ShopEnvironment.connect(config);
             TestUsers first = small.newTestUsers();
             TestUsers second = small.newTestUsers();
             TestUsers third = small.newTestUsers()) {
            first.leasePremium();
            second.leasePremium();

            assertThatThrownBy(third::leasePremium)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("all 2 are leased");
        }
    }

    /**
     * 400 tests on 8 real threads: every name unique (AtomicLong), every user registered for cleanup
     * (ConcurrentLinkedQueue), the rate-limited admin login done once, and cleanup removes everything.
     */
    @Test
    void runWideState_underRealConcurrency() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        Set<String> names = ConcurrentHashMap.newKeySet();
        try {
            List<Future<Credentials>> created = pool.invokeAll(IntStream.range(0, 400)
                    .<java.util.concurrent.Callable<Credentials>>mapToObj(i -> () -> environment.newTestUsers().create())
                    .toList());
            for (Future<Credentials> user : created) {
                names.add(user.get().username());
            }
        } finally {
            pool.shutdown();
        }

        assertThat(names).hasSize(400);
        assertThat(environment.createdUsersToCleanUp()).isEqualTo(400);
        assertThat(environment.adminLogins()).isEqualTo(1);

        environment.close();
        assertThat(names).noneMatch(environment::userExists);
    }

    private void openProfile(FakeWebDriver driver, Credentials user) {
        driver.addCookie("session", api.login(user));
        driver.get("/profile");
    }
}
