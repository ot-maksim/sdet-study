package com.study.principles.parallel.threadlocal;

import com.study.principles.parallel.FakeShop;
import com.study.principles.parallel.FakeWebDriver;

/**
 * The classic fix for a static {@code WebDriver}: one driver per <b>thread</b>. It does fix the race
 * between parallel tests, but it binds the browser to the wrong thing:
 *
 * <ul>
 *   <li><b>Another thread in the same test sees no driver</b> (or another test's). Awaitility evaluates
 *       conditions in its own thread by default, {@code CompletableFuture.supplyAsync} uses the common
 *       pool, a JUnit {@code @Timeout} with {@code SEPARATE_THREAD} runs the test elsewhere.</li>
 *   <li><b>Pool threads are reused.</b> If a test forgets {@link #quit()} (or a base class with a
 *       different lifecycle skips it), the next test on that thread silently gets the previous test's
 *       browser, logged in as someone else.</li>
 *   <li><b>Static access</b> hides the dependency: any class can call {@link #driver()}, nothing says
 *       which test it belongs to.</li>
 * </ul>
 *
 * <p>When ThreadLocal <i>is</i> a reasonable tool:
 * <ul>
 *   <li><b>TestNG with {@code parallel="methods"}</b> is where it is most common: one instance serves all
 *       methods, so fields are shared, while {@code @BeforeMethod}, the test and {@code @AfterMethod} usually
 *       share a thread. Usually: a {@code @Test(timeOut)} run without parallelism gets its own thread. The
 *       test's {@code ITestResult} is the sturdier per-test holder ({@code testng.good.ShopNgTestBase}).</li>
 *   <li><b>The resource itself is bound to a thread.</b> Playwright Java must be used from the thread that
 *       created it, so a per-thread {@code Playwright}/{@code Browser} is fine; the per-test isolation is
 *       still a new {@code BrowserContext} per test.</li>
 * </ul>
 * JUnit 5 has a per-test scope of its own (the test's {@code ExtensionContext.Store}), Cucumber has
 * scenario-scoped dependency injection: there the ThreadLocal is a workaround for a problem that is not there.
 */
public final class DriverManager {

    private static final ThreadLocal<FakeWebDriver> DRIVER = new ThreadLocal<>();

    private DriverManager() {
    }

    public static void start(FakeShop shop) {
        DRIVER.set(new FakeWebDriver(shop));
    }

    public static FakeWebDriver driver() {
        FakeWebDriver driver = DRIVER.get();
        if (driver == null) {
            throw new IllegalStateException("no driver for thread " + Thread.currentThread().getName());
        }
        return driver;
    }

    public static void quit() {
        FakeWebDriver driver = DRIVER.get();
        if (driver != null) {
            driver.quit();
            DRIVER.remove(); // without remove() the thread keeps it for the next test
        }
    }
}
