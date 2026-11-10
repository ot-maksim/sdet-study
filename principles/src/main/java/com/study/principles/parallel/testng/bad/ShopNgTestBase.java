package com.study.principles.parallel.testng.bad;

import com.study.principles.parallel.FakeWebDriver;
import com.study.principles.parallel.good.ProfilePage;
import com.study.principles.parallel.good.ShopApi;
import com.study.principles.parallel.good.ShopConfig;
import com.study.principles.parallel.good.ShopEnvironment;
import com.study.principles.parallel.good.TestUsers;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;

/**
 * The most common TestNG base class: per-test objects in instance fields, set in {@code @BeforeMethod}.
 * No statics, good clients, and still broken in parallel.
 *
 * <h2>What is wrong</h2>
 * TestNG creates <b>one instance per test class</b> and runs all its methods on it (JUnit 5 creates a new
 * instance per test method). So an instance field is per <i>class</i>, not per test:
 * <ul>
 *   <li>{@code parallel="methods"}: test B's {@code @BeforeMethod} overwrites {@code driver} while test A
 *       is using it; A reads B's page. B's {@code @AfterMethod} quits the driver both now point to, and
 *       A's own browser is never quit (an orphaned Grid session).</li>
 *   <li>{@code @DataProvider(parallel = true)} and {@code @Test(threadPoolSize = ..)}: the same, even
 *       inside one method.</li>
 *   <li>With {@code parallel="classes"} or {@code "instances"} it works: methods of one instance run one
 *       after another on one thread. That is why such base classes survive for years, until someone
 *       switches to {@code methods} for a faster build.</li>
 * </ul>
 * Fields are fine for what really is per class: {@code environment} below (and in JUnit 5 the same field
 * would be per test method, so the habit does not transfer either way).
 */
public abstract class ShopNgTestBase {

    protected ShopEnvironment environment;   // per class: fine, the instance is the class
    protected ShopApi api;
    protected FakeWebDriver driver;          // per test? No: per class, shared by its parallel methods
    protected TestUsers users;
    protected ProfilePage profile;

    @BeforeClass(alwaysRun = true)
    public void connect() {
        environment = ShopEnvironment.connect(ShopConfig.DEFAULT);
        api = environment.api();
    }

    @AfterClass(alwaysRun = true)
    public void disconnect() {
        environment.close();
    }

    @BeforeMethod(alwaysRun = true)
    public void openBrowser() {
        driver = environment.newDriver();
        users = environment.newTestUsers();
        profile = new ProfilePage(driver);
    }

    @AfterMethod(alwaysRun = true)
    public void closeBrowser() {
        driver.quit();
        users.close();
    }
}
