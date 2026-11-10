package com.study.principles.parallel;

import com.study.principles.parallel.bad.BaseShopTest;
import com.study.principles.parallel.good.ShopApi;
import com.study.principles.parallel.good.ShopConfig;
import com.study.principles.parallel.good.ShopEnvironment;
import com.study.principles.parallel.good.TestUsers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

/**
 * The same test, "new user sees their own name via API and on the profile page", run as 400 tests on
 * 8 threads: once through the static base class, once through the per-test/run-wide scopes.
 * The bad numbers differ from run to run; that is the point.
 */
public final class ParallelDemo {

    private static final int THREADS = 8;
    private static final int TESTS = 400;

    /** A test class of the bad suite. */
    static final class ProfileTest extends BaseShopTest {
        String run() {
            Credentials me = createUser();
            authorizeAs(me);
            String apiSays = whoAmI();
            openProfileAs(me);
            String pageSays = greetingOnPage();
            if (!apiSays.equals(me.username())) {
                return "API answered as another user";
            }
            if (!pageSays.equals("Hello, " + me.username())) {
                return "page showed another user's session";
            }
            return "ok";
        }

        int registeredForCleanup() {
            return createdUsers.size();
        }

        int createdInShop() {
            return shop.createdUsers();
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=== bad: static RequestSpecification, static WebDriver, counter++, HashMap ===");
        BaseShopTest.startClients();
        Map<String, Integer> bad = runInParallel(() -> new ProfileTest().run());
        print(bad);
        System.out.println("  users created: " + createdInBadSuite() + ", registered for cleanup: " + registeredInBadSuite());

        System.out.println();
        System.out.println("=== good: immutable ShopApi, driver per test, AtomicLong, ConcurrentLinkedQueue ===");
        try (ShopEnvironment environment = ShopEnvironment.connect(ShopConfig.DEFAULT)) {
            Map<String, Integer> good = runInParallel(() -> goodTest(environment));
            print(good);
            System.out.println("  admin logins: " + environment.adminLogins()
                    + ", users created and registered for cleanup: " + environment.createdUsersToCleanUp());
        }
    }

    private static String goodTest(ShopEnvironment environment) {
        try (TestUsers users = environment.newTestUsers(); FakeWebDriver driver = environment.newDriver()) {
            Credentials me = users.create();
            ShopApi api = environment.api().as(environment.api().login(me));
            driver.addCookie("session", environment.api().login(me));
            driver.get("/profile");
            boolean ok = api.me().username().equals(me.username())
                    && driver.findText("greeting").equals("Hello, " + me.username());
            return ok ? "ok" : "saw another user";
        }
    }

    private static Map<String, Integer> runInParallel(Supplier<String> test) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Callable<String>> tests = new ArrayList<>();
        for (int i = 0; i < TESTS; i++) {
            tests.add(() -> {
                try {
                    return test.get();
                } catch (Throwable e) {
                    return classify(e);
                }
            });
        }
        Map<String, Integer> outcomes = new TreeMap<>();
        for (Future<String> f : pool.invokeAll(tests)) {
            try {
                outcomes.merge(f.get(), 1, Integer::sum);
            } catch (Exception e) {
                outcomes.merge("harness error", 1, Integer::sum);
            }
        }
        pool.shutdown();
        return outcomes;
    }

    private static String classify(Throwable e) {
        String message = String.valueOf(e.getMessage());
        if (message.contains("but was <409>")) {
            return "409: duplicate 'unique' name (counter++)";
        }
        if (message.contains("but was <403>")) {
            return "403: created a user with someone else's (non-admin) token";
        }
        if (message.contains("but was <401>")) {
            return "401: the request went out with a broken or missing token";
        }
        return "failed: " + e.getClass().getSimpleName() + ": " + message.lines().reduce((a, b) -> b).orElse("");
    }

    private static void print(Map<String, Integer> outcomes) {
        outcomes.forEach((outcome, count) -> System.out.printf("  %4d  %s%n", count, outcome));
    }

    private static int registeredInBadSuite() {
        return new ProfileTest().registeredForCleanup();
    }

    private static int createdInBadSuite() {
        return new ProfileTest().createdInShop();
    }
}
