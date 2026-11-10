package com.study.principles.parallel.good;

import com.study.principles.parallel.FakeWebDriver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.testkit.engine.EventConditions.event;
import static org.junit.platform.testkit.engine.EventConditions.finishedWithFailure;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.instanceOf;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.message;

/**
 * The scope rules of {@link ShopExtension}, checked by running small test classes through the JUnit
 * engine (EngineTestKit). The nested classes are not run by Surefire on their own (it skips {@code $}
 * classes); they exist only as input for these tests.
 */
class ShopExtensionScopeTest {

    @Test
    void eachParallelTestMethodGetsItsOwnDriver_andBeforeEachGetsTheSameOneAsItsTest() {
        Events tests = run(DriverPerMethod.class);

        tests.assertStatistics(stats -> stats.started(10).succeeded(10));
        assertThat(DriverPerMethod.seen).hasSize(10);
    }

    @Test
    void pageObjectWrapsTheSameDriverAsTheTest() {
        run(PageAndDriver.class).assertStatistics(stats -> stats.started(5).succeeded(5));
    }

    @Test
    void perTestObjectInBeforeAll_isRefused_insteadOfBeingSharedByTheWholeClass() {
        Events containers = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(DriverInBeforeAll.class))
                .execute().containerEvents();

        containers.assertThatEvents().haveExactly(1, event(finishedWithFailure(
                instanceOf(ParameterResolutionException.class),
                message(m -> m.contains("FakeWebDriver is per test method") && m.contains("whole class")))));
    }

    @Test
    void perTestObjectInPerClassConstructor_isRefused() {
        var results = EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(DriverInPerClassConstructor.class))
                .execute();

        results.testEvents().assertStatistics(stats -> stats.succeeded(0));
        results.containerEvents()
                .assertThatEvents().haveExactly(1, event(finishedWithFailure(
                        instanceOf(ParameterResolutionException.class),
                        message(m -> m.contains("is per test method")))));
    }

    @Test
    void runScopedObjectsAreFineAtClassLevel() {
        run(EnvironmentInBeforeAll.class).assertStatistics(stats -> stats.started(1).succeeded(1));
    }

    private static Events run(Class<?> testClass) {
        return EngineTestKit.engine("junit-jupiter")
                .selectors(selectClass(testClass))
                .execute().testEvents();
    }

    // ---- input classes ---------------------------------------------------------------------------

    @ExtendWith(ShopExtension.class)
    @Execution(ExecutionMode.CONCURRENT)
    static class DriverPerMethod {
        static final Set<FakeWebDriver> seen = ConcurrentHashMap.newKeySet();
        private FakeWebDriver fromBeforeEach;

        @BeforeEach
        void setUp(FakeWebDriver driver) {
            fromBeforeEach = driver;
        }

        @RepeatedTest(10)
        void test(FakeWebDriver driver) {
            assertThat(driver).isSameAs(fromBeforeEach);
            seen.add(driver);
        }
    }

    @ExtendWith(ShopExtension.class)
    @Execution(ExecutionMode.CONCURRENT)
    static class PageAndDriver {
        @RepeatedTest(5)
        void test(ProfilePage page, FakeWebDriver driver) {
            page.open();                                    // not logged in: redirected
            assertThat(driver.getCurrentUrl()).isEqualTo("/login");
        }
    }

    @ExtendWith(ShopExtension.class)
    static class DriverInBeforeAll {
        @BeforeAll
        static void setUp(FakeWebDriver driver) {
        }

        @Test
        void test() {
        }
    }

    @ExtendWith(ShopExtension.class)
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    static class DriverInPerClassConstructor {
        DriverInPerClassConstructor(FakeWebDriver driver) {
        }

        @Test
        void test() {
        }
    }

    @ExtendWith(ShopExtension.class)
    static class EnvironmentInBeforeAll {
        static ShopEnvironment environment;

        @BeforeAll
        static void setUp(ShopEnvironment env, ShopApi api) {
            environment = env;
        }

        @Test
        void test() {
            assertThat(environment).isNotNull();
        }
    }
}
