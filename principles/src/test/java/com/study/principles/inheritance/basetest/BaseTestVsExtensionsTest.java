package com.study.principles.inheritance.basetest;

import com.study.principles.inheritance.basetest.bad.BaseTest;
import com.study.principles.inheritance.basetest.bad.split.DbBaseTest;
import com.study.principles.inheritance.basetest.bad.split.UiDbBaseTest;
import com.study.principles.inheritance.basetest.good.ApiTest;
import com.study.principles.inheritance.basetest.good.Checks;
import com.study.principles.inheritance.basetest.good.DbTest;
import com.study.principles.inheritance.basetest.good.UiTest;
import com.study.principles.inheritance.basetest.good.mixins.DbAssertions;
import com.study.principles.inheritance.basetest.good.mixins.UiAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.testkit.engine.EventConditions.finishedWithFailure;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.instanceOf;

/**
 * Runs small test classes through the JUnit engine and reads {@link Startups}: what each style of fixture
 * started for the same kind of test. The {@code $}-classes at the bottom are input only (Surefire skips them).
 */
class BaseTestVsExtensionsTest {

    @BeforeEach
    void clearProbe() {
        Startups.clear();
    }

    @Nested
    class Bad {

        @Test
        void anApiTestPaysForABrowserAndADbConnection() {
            run(BadApiOnly.class).assertStatistics(stats -> stats.started(1).succeeded(1));

            assertThat(Startups.log()).containsExactly(
                    "api client", "db connected", "browser launched", "browser closed", "db closed");
        }

        @Test
        void aSubclassSetUpWithTheSameNameSilentlyReplacesTheBaseOne() {
            Events events = run(BadShadowedSetUp.class);

            // the test was written against fields the base class "always" fills
            events.assertStatistics(stats -> stats.started(1).failed(1));
            events.failed().assertThatEvents().haveExactly(1, finishedWithFailure(instanceOf(NullPointerException.class)));
            assertThat(Startups.log()).as("base setUp() never ran").isEmpty();
        }

        @Test
        void uiPlusDbCopiesTheDbHalfBecauseThereIsOnlyOneSuperclass() {
            run(BadUiAndDb.class).assertStatistics(stats -> stats.started(1).succeeded(1));

            assertThat(DbBaseTest.class.isAssignableFrom(UiDbBaseTest.class)).as("UiDbBaseTest is not a DbBaseTest").isFalse();
            assertThat(declaredMethodNames(UiDbBaseTest.class))
                    .as("the same lifecycle and helper, written twice")
                    .containsAll(declaredMethodNames(DbBaseTest.class));
        }
    }

    @Nested
    class Good {

        @Test
        void anApiTestStartsOnlyWhatItAsksFor() {
            run(GoodApiOnly.class).assertStatistics(stats -> stats.started(1).succeeded(1));

            assertThat(Startups.log()).containsExactly("api client");
        }

        @Test
        void uiAndDbComposeFromTwoAnnotationsWithoutANewClass() {
            run(GoodUiAndDb.class).assertStatistics(stats -> stats.started(1).succeeded(1));

            assertThat(Startups.log())
                    .containsOnlyOnce("api client", "browser launched", "db connected")
                    .contains("browser closed", "db closed");
        }

        @Test
        void aTestMayHaveItsOwnSetUpNothingToShadow() {
            run(GoodOwnSetUp.class).assertStatistics(stats -> stats.started(1).succeeded(1));
        }

        @Test
        void aFailingUiTestGetsAScreenshotAndTheBrowserIsStillClosed() {
            run(GoodFailingUi.class).assertStatistics(stats -> stats.started(1).failed(1));

            assertThat(Startups.log()).containsSubsequence(
                    "browser launched", "screenshot test", "browser closed");
        }

        @Test
        void aFailingUiTestThatNeverOpenedABrowserDoesNotLaunchOneForTheScreenshot() {
            run(GoodFailingWithoutBrowser.class).assertStatistics(stats -> stats.started(1).failed(1));

            assertThat(Startups.log()).doesNotContain("browser launched");
        }

        @Test
        void testInterfacesMixInBehaviourFromSeveralSources() {
            run(GoodMixins.class).assertStatistics(stats -> stats.started(1).succeeded(1));

            assertThat(Startups.log()).contains("browser launched", "db connected");
        }
    }

    private static Events run(Class<?> testClass) {
        return EngineTestKit.engine("junit-jupiter").selectors(selectClass(testClass)).execute().testEvents();
    }

    private static List<String> declaredMethodNames(Class<?> type) {
        return Arrays.stream(type.getDeclaredMethods()).map(java.lang.reflect.Method::getName).toList();
    }

    // ---- input classes: bad --------------------------------------------------------------------------

    static class BadApiOnly extends BaseTest {
        @Test
        void test() {
            User created = api.createUser(randomName("api"));
            assertThat(created.id()).isPositive();
        }
    }

    static class BadShadowedSetUp extends BaseTest {
        private String reportName;

        /** "I just need to prepare my own data" — and the base setUp() is gone. */
        @BeforeEach
        @Override
        protected void setUp() {
            reportName = "monthly";
        }

        @Test
        void test() {
            assertThat(browser.greeting()).isEqualTo("Hello, default-user");
        }
    }

    static class BadUiAndDb extends UiDbBaseTest {
        @Test
        void test() {
            assertStored(user);
            assertThat(browser.greeting()).isEqualTo("Hello, ui-user");
        }
    }

    // ---- input classes: good -------------------------------------------------------------------------

    @ApiTest
    static class GoodApiOnly {
        @Test
        void test(ApiClient api) {
            assertThat(api.createUser("api").id()).isPositive();
        }
    }

    @UiTest
    @DbTest
    static class GoodUiAndDb {
        @Test
        void test(User user, Browser browser, Db db) {
            browser.loginAs(user);

            Checks.assertStored(db, user);
            assertThat(browser.greeting()).isEqualTo("Hello, test");
        }
    }

    @UiTest
    static class GoodOwnSetUp {
        private String reportName;

        @BeforeEach
        void setUp() {
            reportName = "monthly";
        }

        @Test
        void test(User user, Browser browser) {
            browser.loginAs(user);
            assertThat(browser.greeting()).isEqualTo("Hello, test");
            assertThat(reportName).isEqualTo("monthly");
        }
    }

    @UiTest
    static class GoodFailingUi {
        @Test
        void test(Browser browser) {
            throw new AssertionError("the page shows the wrong total");
        }
    }

    @UiTest
    static class GoodFailingWithoutBrowser {
        @Test
        void test(User user) {
            throw new AssertionError("failed before the UI part");
        }
    }

    static class GoodMixins implements UiAssertions, DbAssertions {
        @Test
        void test(User user, Browser browser, Db db) {
            browser.loginAs(user);

            assertGreets(browser, user);
            assertStored(db, user);
        }
    }
}
