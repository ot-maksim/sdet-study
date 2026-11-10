package com.study.principles.parallel.testng;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * Executable facts behind the JUnit 5 vs TestNG differences that decide where per-test state may live.
 * Each fact runs small test classes through the real engine (EngineTestKit; "testng" is the TestNG engine
 * for the JUnit Platform). The input classes are package-private nested classes: the normal build does
 * not discover them, they run only from here.
 */
class JUnitVsTestNgLifecycleTest {

    // ---- 1. Who owns an instance field ------------------------------------------------------------

    @Test
    void junit_createsANewInstancePerTestMethod_andPerParameterizedInvocation() {
        JUnitInstances.seen.clear();
        jupiter(false, selectClass(JUnitInstances.class)).assertStatistics(s -> s.succeeded(5));

        assertThat(JUnitInstances.seen).hasSize(5);
    }

    @Test
    void testng_runsAllMethodsAndDataProviderInvocationsOnOneInstance() {
        TestNgInstances.seen.clear();
        testng("none", selectClass(TestNgInstances.class)).assertStatistics(s -> s.succeeded(5));

        assertThat(TestNgInstances.seen).hasSize(1);
    }

    // ---- 2. A field set in the per-test setup, two tests in parallel ------------------------------

    @Test
    void junit_fieldSetInBeforeEach_isPerTest_evenInParallel() {
        jupiter(true, selectClass(JUnitFieldRace.class)).assertStatistics(s -> s.succeeded(2));
    }

    @Test
    void testng_fieldSetInBeforeMethod_isOverwrittenByTheOtherTest() {
        testng("methods", selectClass(TestNgFieldRace.class))
                .assertStatistics(s -> s.succeeded(1).failed(1));   // the first setup lost its field
    }

    /**
     * TestNG's own way to make fields per test: one instance per data row, parallel by instances.
     * (The input is a top-level class: TestNG needs a public class for {@code @Factory}, and a public nested
     * class would be picked up by the normal build.)
     */
    @Test
    void testng_factoryWithParallelInstances_makesFieldsPerInstance() {
        testng("instances", selectClass(FactoryInstancesInput.class)).assertStatistics(s -> s.succeeded(2));
    }

    // ---- 3. Which thread runs the test body -------------------------------------------------------

    @Test
    void testng_threadLocalFromBeforeMethod_worksInParallelRun() {
        testng("methods", selectClass(TestNgThreadLocalWithTimeout.class)).assertStatistics(s -> s.succeeded(1));
    }

    /** One test from the IDE = no parallelism; with timeOut the body runs on its own thread. */
    @Test
    void testng_threadLocalFromBeforeMethod_isEmpty_whenTimeOutRunsWithoutParallelism() {
        testng("none", selectClass(TestNgThreadLocalWithTimeout.class)).assertStatistics(s -> s.failed(1));
    }

    // ---- engines ----------------------------------------------------------------------------------

    private static Events jupiter(boolean parallel, DiscoverySelector selector) {
        return EngineTestKit.engine("junit-jupiter")
                .configurationParameter("junit.jupiter.execution.parallel.enabled", String.valueOf(parallel))
                .configurationParameter("junit.jupiter.execution.parallel.config.strategy", "fixed")
                .configurationParameter("junit.jupiter.execution.parallel.config.fixed.parallelism", "4")
                .selectors(selector)
                .execute().testEvents();
    }

    static Events testng(String parallel, DiscoverySelector... selectors) {
        return EngineTestKit.engine("testng")
                .configurationParameter("testng.parallel", parallel)
                .configurationParameter("testng.threadCount", "4")
                .configurationParameter("testng.dataProviderThreadCount", "4")
                .selectors(selectors)
                .execute().testEvents();
    }

    static void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("the other test never arrived: not running in parallel?", e);
        }
    }

    // ---- input classes: JUnit ---------------------------------------------------------------------

    static class JUnitInstances {
        static final Set<Object> seen = ConcurrentHashMap.newKeySet();

        @Test
        void a() {
            seen.add(this);
        }

        @Test
        void b() {
            seen.add(this);
        }

        @ParameterizedTest
        @ValueSource(ints = {1, 2, 3})
        void data(int row) {
            seen.add(this);
        }
    }

    @Execution(ExecutionMode.CONCURRENT)
    static class JUnitFieldRace {
        static final CyclicBarrier bothSetUp = new CyclicBarrier(2);
        private String driver;   // stands for a per-test browser

        @BeforeEach
        void openBrowser(org.junit.jupiter.api.TestInfo test) {
            driver = "browser of " + test.getDisplayName();
        }

        @Test
        void a(org.junit.jupiter.api.TestInfo test) {
            await(bothSetUp);    // both setups have run before either test reads its field
            assertThat(driver).isEqualTo("browser of " + test.getDisplayName());
        }

        @Test
        void b(org.junit.jupiter.api.TestInfo test) {
            await(bothSetUp);
            assertThat(driver).isEqualTo("browser of " + test.getDisplayName());
        }
    }

    // ---- input classes: TestNG --------------------------------------------------------------------

    static class TestNgInstances {
        static final Set<Object> seen = ConcurrentHashMap.newKeySet();

        @org.testng.annotations.Test
        public void a() {
            seen.add(this);
        }

        @org.testng.annotations.Test
        public void b() {
            seen.add(this);
        }

        @DataProvider(parallel = true)
        public Object[][] rows() {
            return new Object[][]{{1}, {2}, {3}};
        }

        @org.testng.annotations.Test(dataProvider = "rows")
        public void data(int row) {
            seen.add(this);
        }
    }

    static class TestNgFieldRace {
        static final CyclicBarrier bothSetUp = new CyclicBarrier(2);
        private String driver;

        @BeforeMethod
        public void openBrowser(java.lang.reflect.Method test) {
            driver = "browser of " + test.getName();
        }

        @org.testng.annotations.Test
        public void a() {
            await(bothSetUp);
            assertThat(driver).isEqualTo("browser of a");
        }

        @org.testng.annotations.Test
        public void b() {
            await(bothSetUp);
            assertThat(driver).isEqualTo("browser of b");
        }
    }

    static class TestNgThreadLocalWithTimeout {
        private static final ThreadLocal<String> DRIVER = new ThreadLocal<>();

        @BeforeMethod
        public void openBrowser() {
            DRIVER.set("browser");
        }

        @org.testng.annotations.AfterMethod(alwaysRun = true)
        public void closeBrowser() {
            DRIVER.remove();
        }

        @org.testng.annotations.Test(timeOut = 5_000)
        public void profile() {
            assertThat(DRIVER.get()).as("driver on " + Thread.currentThread().getName()).isEqualTo("browser");
        }
    }
}
