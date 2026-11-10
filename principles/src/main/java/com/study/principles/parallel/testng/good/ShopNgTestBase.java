package com.study.principles.parallel.testng.good;

import com.study.principles.parallel.FakeWebDriver;
import com.study.principles.parallel.good.ProfilePage;
import com.study.principles.parallel.good.ShopApi;
import com.study.principles.parallel.good.ShopConfig;
import com.study.principles.parallel.good.ShopEnvironment;
import com.study.principles.parallel.good.TestUsers;
import org.testng.IHookCallBack;
import org.testng.IHookable;
import org.testng.ITestResult;
import org.testng.Reporter;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * The same scopes as {@code ShopExtension}, built from what TestNG offers. TestNG has no parameter
 * resolver and no per-test Store, and one instance serves all methods of a class, so neither parameters
 * nor fields can carry per-test objects. What TestNG does have per test invocation is the
 * {@link ITestResult}:
 *
 * <table>
 *   <tr><th>Scope</th><th>JUnit 5 ({@code ShopExtension})</th><th>TestNG (here)</th></tr>
 *   <tr><td>run</td><td>root {@code Store}</td><td>holder idiom (JVM class init: once, thread-safe) +
 *       {@code @AfterSuite} cleanup</td></tr>
 *   <tr><td>test</td><td>the test's {@code Store}, injected as parameters</td><td>an attribute of the
 *       test's {@code ITestResult}, set in {@code @BeforeMethod}, closed in {@code @AfterMethod(alwaysRun)};
 *       read through {@code driver()}, {@code profile()}, ...</td></tr>
 *   <tr><td>lock</td><td>{@code @ResourceLock}: the scheduler does not start conflicting tests</td>
 *       <td>{@link SharedResource} + {@link IHookable}: a {@code ReentrantReadWriteLock} around the test
 *       body; the thread waits (holding a pool slot and an open browser)</td></tr>
 * </table>
 *
 * <h2>Why ITestResult and not the usual ThreadLocal</h2>
 * A hand-made {@code ThreadLocal} set in {@code @BeforeMethod} works while the configuration method and
 * the test body share a thread. They usually do, but a {@code @Test(timeOut = ..)} run without parallelism
 * (one test from the IDE) executes the body on its own thread: the ThreadLocal is empty there, so the test
 * passes in CI and fails on a laptop. TestNG itself makes the current {@code ITestResult} available on
 * that thread ({@link Reporter#getCurrentTestResult()}), so a scope stored in it follows the test.
 *
 * <p>Still thread-bound: a thread TestNG does not know (CompletableFuture, Awaitility's poller) sees no
 * current test. Get {@code driver()} on the test thread and pass the object in explicitly.
 */
public abstract class ShopNgTestBase implements IHookable {

    private static final String SCOPE = ShopNgTestBase.class.getName() + ".scope";
    private static final ConcurrentMap<String, ReadWriteLock> LOCKS = new ConcurrentHashMap<>();

    /** Holder idiom: initialized by the JVM on first use, exactly once, safely published to every thread. */
    private static final class Run {
        static final ShopEnvironment ENVIRONMENT = ShopEnvironment.connect(ShopConfig.DEFAULT);
    }

    protected static ShopEnvironment environment() {
        return Run.ENVIRONMENT;
    }

    @AfterSuite(alwaysRun = true)
    public void cleanUpRun() {
        environment().close();
    }

    @BeforeMethod(alwaysRun = true)
    public void openTestScope(ITestResult test) {
        test.setAttribute(SCOPE, TestScope.open(environment()));
    }

    @AfterMethod(alwaysRun = true)
    public void closeTestScope(ITestResult test) {
        if (test.removeAttribute(SCOPE) instanceof TestScope scope) {
            scope.close();
        }
    }

    // ---- what tests use ---------------------------------------------------------------------------

    protected ShopApi api() {
        return environment().api();
    }

    protected FakeWebDriver driver() {
        return scope().driver();
    }

    protected ProfilePage profile() {
        return scope().profile();
    }

    protected TestUsers users() {
        return scope().users();
    }

    private static TestScope scope() {
        ITestResult current = Reporter.getCurrentTestResult();
        if (current != null && current.getAttribute(SCOPE) instanceof TestScope scope) {
            return scope;
        }
        throw new IllegalStateException("no test scope on thread " + Thread.currentThread().getName()
                + ": called outside a test method (@BeforeClass shares it with every test of the class) or on a"
                + " thread TestNG does not know (CompletableFuture, Awaitility): get driver() on the test"
                + " thread and pass it in");
    }

    // ---- @SharedResource ------------------------------------------------------------------------

    @Override
    public void run(IHookCallBack callBack, ITestResult test) {
        SharedResource shared = test.getMethod().getConstructorOrMethod().getMethod()
                .getAnnotation(SharedResource.class);
        if (shared == null) {
            callBack.runTestMethod(test);
            return;
        }
        ReadWriteLock readWrite = LOCKS.computeIfAbsent(shared.value(), name -> new ReentrantReadWriteLock(true));
        Lock lock = shared.mode() == SharedResource.Mode.READ ? readWrite.readLock() : readWrite.writeLock();
        lock.lock();
        try {
            callBack.runTestMethod(test);
        } finally {
            lock.unlock();
        }
    }

    /** Everything one test owns. Closed in {@code @AfterMethod(alwaysRun = true)}, also when the test failed. */
    private record TestScope(FakeWebDriver driver, ProfilePage profile, TestUsers users) implements AutoCloseable {

        static TestScope open(ShopEnvironment environment) {
            FakeWebDriver driver = environment.newDriver();
            return new TestScope(driver, new ProfilePage(driver), environment.newTestUsers());
        }

        @Override
        public void close() {
            driver.quit();
            users.close();
        }
    }
}
