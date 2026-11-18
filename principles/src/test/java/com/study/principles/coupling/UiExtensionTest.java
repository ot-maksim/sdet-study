package com.study.principles.coupling;

import com.study.principles.coupling.good.Browsers;
import com.study.principles.coupling.good.Pages;
import com.study.principles.coupling.good.root.UiExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * The composition root's contract, checked by running small test classes through the JUnit engine.
 * The nested classes are input only (Surefire skips {@code $} classes).
 */
class UiExtensionTest {

    @Test
    void pagesAndBrowserOfOneTestAreOneSession() {
        run(OneSession.class).assertStatistics(stats -> stats.started(1).succeeded(1));
    }

    @Test
    void everyTestGetsItsOwnBrowserAndItIsClosedAfterTheTest() {
        PerTest.seen.clear();

        run(PerTest.class).assertStatistics(stats -> stats.started(3).succeeded(3));

        assertThat(PerTest.seen).hasSize(3).doesNotHaveDuplicates().allMatch(Browser::isClosed);
    }

    @Test
    void browsersAreClosedAlsoWhenTheTestFails() {
        Failing.seen.clear();

        run(Failing.class).assertStatistics(stats -> stats.started(1).failed(1));

        assertThat(Failing.seen).hasSize(2).allMatch(Browser::isClosed);
    }

    @Test
    void extraSessionsFromBeforeEachDoNotReplaceTheMainOne() {
        run(ExtraSessionFirst.class).assertStatistics(stats -> stats.started(1).succeeded(1));
    }

    private static Events run(Class<?> testClass) {
        return EngineTestKit.engine("junit-jupiter").selectors(selectClass(testClass)).execute().testEvents();
    }

    // ---- input classes ---------------------------------------------------------------------------

    @ExtendWith(UiExtension.class)
    static class OneSession {
        @Test
        void test(Pages pages, Browser browser) {
            pages.login().open().loginAs("alice", "alice-pass");

            assertThat(browser.currentPath()).isEqualTo("/dashboard");
        }
    }

    @ExtendWith(UiExtension.class)
    static class PerTest {
        static final List<Browser> seen = new CopyOnWriteArrayList<>();

        @RepeatedTest(3)
        void test(Browser browser) {
            assertThat(browser.isClosed()).isFalse();
            seen.add(browser);
        }
    }

    @ExtendWith(UiExtension.class)
    static class Failing {
        static final List<Browser> seen = new CopyOnWriteArrayList<>();

        @Test
        void test(Browser browser, Browsers browsers) {
            seen.add(browser);
            seen.add(browsers.launch());
            throw new AssertionError("the test fails; nobody wrote a teardown");
        }
    }

    @ExtendWith(UiExtension.class)
    static class ExtraSessionFirst {
        private Pages admin;

        @BeforeEach
        void setUp(Browsers browsers) {
            admin = browsers.newSession();
            admin.login().open().loginAs("bob", "bob-pass");
        }

        @Test
        void test(Pages pages) {
            pages.login().open().loginAs("alice", "alice-pass");

            assertThat(pages.dashboard().greeting()).isEqualTo("Hello, alice");
            assertThat(admin.dashboard().greeting()).isEqualTo("Hello, bob");
        }
    }
}
