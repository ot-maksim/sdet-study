package com.study.principles.parallel.testng;

import com.study.principles.parallel.Credentials;
import com.study.principles.parallel.FakeWebDriver;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;
import org.testng.annotations.BeforeClass;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CyclicBarrier;

import static com.study.principles.parallel.testng.JUnitVsTestNgLifecycleTest.await;
import static com.study.principles.parallel.testng.JUnitVsTestNgLifecycleTest.testng;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

/**
 * The TestNG side of {@code Bad/GoodSharedStateTest}: the same profile scenario on the two TestNG base
 * classes, run by the real TestNG engine with {@code parallel="methods"}. Barriers make the overlap
 * certain: both setups run, then both tests open their profile, then both read it.
 */
class TestNgSharedStateTest {

    @Test
    void badBase_fieldsArePerClass_soOneOfTwoParallelTestsSeesTheOthersProfile() {
        testng("methods", selectClass(ProfileOnBadBase.class)).assertStatistics(s -> s.succeeded(1).failed(1));
    }

    @Test
    void badBase_isFineWithoutMethodParallelism_whichIsWhyItSurvives() {
        testng("classes", selectClass(SequentialProfileOnBadBase.class)).assertStatistics(s -> s.succeeded(2));
    }

    @Test
    void goodBase_scopeLivesInTheTestResult_soEachParallelTestKeepsItsOwnBrowser() {
        testng("methods", selectClass(ProfileOnGoodBase.class)).assertStatistics(s -> s.succeeded(2));
    }

    /** The case where a hand-made ThreadLocal fails (see JUnitVsTestNgLifecycleTest): no parallelism + timeOut. */
    @Test
    void goodBase_followsTheTestOntoTheTimeOutThread() {
        testng("none", selectClass(GoodBaseWithTimeout.class)).assertStatistics(s -> s.succeeded(1));
    }

    @Test
    void goodBase_unknownThreadGetsAClearError_explicitlyPassedDriverWorks() {
        testng("methods", selectClass(GoodBaseAsync.class)).assertStatistics(s -> s.succeeded(1));
    }

    @Test
    void goodBase_perTestObjectInBeforeClass_failsWithAClearMessage() {
        var events = EngineTestKit.engine("testng").selectors(selectClass(GoodBaseInBeforeClass.class))
                .execute().allEvents();

        assertThat(events.stream().map(Event::getPayload).flatMap(java.util.Optional::stream).map(String::valueOf))
                .anyMatch(payload -> payload.contains("no test scope") && payload.contains("@BeforeClass"));
    }

    // ---- input classes ----------------------------------------------------------------------------

    static class ProfileOnBadBase extends com.study.principles.parallel.testng.bad.ShopNgTestBase {
        static final CyclicBarrier setUp = new CyclicBarrier(2);
        static final CyclicBarrier opened = new CyclicBarrier(2);

        @org.testng.annotations.Test
        public void alice() {
            scenario();
        }

        @org.testng.annotations.Test
        public void bob() {
            scenario();
        }

        private void scenario() {
            await(setUp);                                   // both @BeforeMethods have written the fields
            Credentials me = users.create();
            profile.openWithSession(api.login(me));         // `profile` is whichever setup ran last
            await(opened);
            assertThat(profile.greeting()).isEqualTo("Hello, " + me.username());
        }
    }

    static class SequentialProfileOnBadBase extends com.study.principles.parallel.testng.bad.ShopNgTestBase {
        @org.testng.annotations.Test
        public void alice() {
            scenario();
        }

        @org.testng.annotations.Test
        public void bob() {
            scenario();
        }

        private void scenario() {
            Credentials me = users.create();
            profile.openWithSession(api.login(me));
            assertThat(profile.greeting()).isEqualTo("Hello, " + me.username());
        }
    }

    static class ProfileOnGoodBase extends com.study.principles.parallel.testng.good.ShopNgTestBase {
        static final CyclicBarrier setUp = new CyclicBarrier(2);
        static final CyclicBarrier opened = new CyclicBarrier(2);

        @org.testng.annotations.Test
        public void alice() {
            scenario();
        }

        @org.testng.annotations.Test
        public void bob() {
            scenario();
        }

        private void scenario() {
            await(setUp);
            Credentials me = users().create();
            profile().openWithSession(api().login(me));
            await(opened);
            assertThat(profile().greeting()).isEqualTo("Hello, " + me.username());
        }
    }

    static class GoodBaseWithTimeout extends com.study.principles.parallel.testng.good.ShopNgTestBase {
        @org.testng.annotations.Test(timeOut = 5_000)
        public void profileWithinFiveSeconds() {
            Credentials me = users().create();
            assertThat(profile().openWithSession(api().login(me)).greeting()).isEqualTo("Hello, " + me.username());
        }
    }

    static class GoodBaseAsync extends com.study.principles.parallel.testng.good.ShopNgTestBase {
        @org.testng.annotations.Test
        public void conditionCheckedOnAnotherThread() {
            Credentials me = users().create();
            profile().openWithSession(api().login(me));

            assertThatThrownBy(() -> CompletableFuture.supplyAsync(() -> driver().findText("greeting")).join())
                    .isInstanceOf(CompletionException.class)
                    .hasMessageContaining("no test scope");

            FakeWebDriver driver = driver();                // taken on the test thread, passed explicitly
            assertThat(CompletableFuture.supplyAsync(() -> driver.findText("greeting")).join())
                    .isEqualTo("Hello, " + me.username());
        }
    }

    static class GoodBaseInBeforeClass extends com.study.principles.parallel.testng.good.ShopNgTestBase {
        @BeforeClass
        public void openProfileOnceForAllTests() {
            profile().open();
        }

        @org.testng.annotations.Test
        public void test() {
        }
    }
}
