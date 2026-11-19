package com.study.framework.core.inject;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import com.study.framework.core.config.Config;
import com.study.framework.core.data.Unique;
import com.study.framework.core.lifecycle.Cleanup;
import com.study.framework.core.lifecycle.CleanupFailedException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Event;
import org.junit.platform.testkit.engine.Events;

/**
 * Runs small test classes (nested, so Surefire does not pick them up) through the Jupiter engine and checks
 * what the composition root did: scopes, closing order, refused wiring.
 */
class CompositionRootTest {

    static final List<String> LOG = Collections.synchronizedList(new ArrayList<>());

    /** Run-wide and expensive: a connection pool, a browser process. */
    static final class Connection implements AutoCloseable {
        Connection() {
            LOG.add("open connection");
        }

        @Override
        public void close() {
            LOG.add("close connection");
        }
    }

    /** Per test: a page, a logged-in session. */
    static final class Session implements AutoCloseable {
        private static int counter;
        final int number = ++counter;
        final Connection connection;

        Session(Connection connection) {
            this.connection = connection;
            LOG.add("open session " + number);
        }

        @Override
        public void close() {
            LOG.add("close session " + number);
        }
    }

    static final class Root extends CompositionRoot {
        @Override
        protected void bind(Bindings b) {
            b.run(Connection.class, c -> c.cleanup().own(new Connection()));
            b.test(Session.class, c -> c.cleanup().own(new Session(c.get(Connection.class))));
        }
    }

    // ---------------------------------------------------------------- scopes

    @ExtendWith(Root.class)
    static class TwoTests {
        static final List<Object> SEEN = Collections.synchronizedList(new ArrayList<>());

        @BeforeEach
        void before(Session session) {
            SEEN.add(session);
        }

        @Test
        void first(Session session, Connection connection, Cleanup cleanup) {
            SEEN.add(session);
            SEEN.add(connection);
            cleanup.defer("delete user of first", () -> LOG.add("delete user of first"));
        }

        @Test
        void second(Session session, Connection connection) {
            SEEN.add(session);
            SEEN.add(connection);
            throw new AssertionError("test failed");
        }
    }

    @Test
    void runObjectsAreSharedTestObjectsAreFreshAndAllAreClosedInReverseOrderAlsoAfterAFailure() {
        LOG.clear();
        Session.counter = 0;
        TwoTests.SEEN.clear();

        Events tests = run(TwoTests.class).testEvents();

        tests.assertStatistics(s -> s.succeeded(1).failed(1));
        // per test, in the order JUnit picked: session in @BeforeEach, session, connection
        List<Object> seen = TwoTests.SEEN;
        assertThat(seen.get(0)).isSameAs(seen.get(1));
        assertThat(seen.get(3)).isSameAs(seen.get(4));
        assertThat(seen.get(1)).isNotSameAs(seen.get(4));
        assertThat(seen.get(2)).isSameAs(seen.get(5));
        assertThat(LOG).containsExactlyInAnyOrder(
                "open connection",
                "open session 1", "delete user of first", "close session 1",
                "open session 2", "close session 2",
                "close connection");
        assertThat(LOG.get(0)).isEqualTo("open connection");
        assertThat(LOG.get(LOG.size() - 1)).isEqualTo("close connection");
        // the test's own cleanup runs before the session it was registered after is closed
        assertThat(LOG.get(LOG.indexOf("delete user of first") + 1)).startsWith("close session");
    }

    // ---------------------------------------------------------------- refused wiring

    @ExtendWith(Root.class)
    static class SessionInBeforeAll {
        @BeforeAll
        static void setUp(Session session) {
        }

        @Test
        void test() {
        }
    }

    @Test
    void aTestObjectInBeforeAllIsRefused() {
        assertThat(failures(run(SessionInBeforeAll.class).containerEvents()))
                .anyMatch(m -> m.contains("Session lives per test, but setUp runs once for")
                        && m.contains("all its tests would share one"));
    }

    static final class Report {
        Report(Session session) {
        }
    }

    static final class CaptiveRoot extends CompositionRoot {
        @Override
        protected void bind(Bindings b) {
            b.run(Connection.class, c -> c.cleanup().own(new Connection()));
            b.test(Session.class, c -> c.cleanup().own(new Session(c.get(Connection.class))));
            b.run(Report.class, c -> new Report(c.get(Session.class)));
        }
    }

    @ExtendWith(CaptiveRoot.class)
    static class AsksForCaptive {
        @Test
        void test(Report report) {
        }
    }

    @Test
    void aRunObjectDependingOnATestObjectIsACaptiveDependency() {
        assertThat(failures(run(AsksForCaptive.class).testEvents()))
                .singleElement().asString()
                .contains("Captive dependency (Report -> Session)")
                .contains("Report lives for the whole run and would keep the first test's Session");
    }

    record A(Object b) {
    }

    record B(Object a) {
    }

    static final class CyclicRoot extends CompositionRoot {
        @Override
        protected void bind(Bindings b) {
            b.test(A.class, c -> new A(c.get(B.class)));
            b.test(B.class, c -> new B(c.get(A.class)));
        }
    }

    @ExtendWith(CyclicRoot.class)
    static class AsksForCycle {
        @Test
        void test(A a) {
        }
    }

    @Test
    void aCycleIsReportedWithItsPath() {
        assertThat(failures(run(AsksForCycle.class).testEvents()))
                .singleElement().asString().contains("Dependency cycle (A -> B -> A)");
    }

    // ---------------------------------------------------------------- cleanup failures, built-ins

    @ExtendWith(Root.class)
    static class LeakingTest {
        @Test
        void passesButCannotCleanUp(Cleanup cleanup) {
            cleanup.defer("delete user 42", () -> {
                throw new IllegalStateException("503 from the SUT");
            });
        }
    }

    @Test
    void aFailedCleanupFailsTheTestInsteadOfLeakingSilently() {
        Events tests = run(LeakingTest.class).testEvents();

        tests.assertStatistics(s -> s.failed(1));
        Throwable error = tests.failed().stream().findFirst().orElseThrow()
                .getPayload(TestExecutionResult.class).orElseThrow().getThrowable().orElseThrow();
        // JUnit reports it as "Failed to close extension context", caused by ours
        assertThat(error.getCause()).isInstanceOf(CleanupFailedException.class)
                .hasMessageContaining("delete user 42: java.lang.IllegalStateException: 503 from the SUT");
    }

    @ExtendWith(Root.class)
    static class BuiltIns {
        static Config config;
        static Unique unique;

        @Test
        void test(Config c, Unique u) {
            config = c;
            unique = u;
        }
    }

    @Test
    void configAndUniqueAreBuiltIn() {
        run(BuiltIns.class).testEvents().assertStatistics(s -> s.succeeded(1));

        assertThat(BuiltIns.config.env()).isEqualTo("local");
        assertThat(BuiltIns.unique.email("ada")).endsWith("@example.test");
    }

    @ExtendWith(Root.class)
    static class Unbound {
        @Test
        void test(StringBuilder notBound) {
        }
    }

    @Test
    void anUnboundTypeIsLeftToOtherResolvers() {
        assertThat(failures(run(Unbound.class).testEvents()))
                .singleElement().asString().contains("No ParameterResolver registered for parameter [java.lang.StringBuilder");
    }

    // ----------------------------------------------------------------

    private static org.junit.platform.testkit.engine.EngineExecutionResults run(Class<?> testClass) {
        return EngineTestKit.engine("junit-jupiter").selectors(selectClass(testClass)).execute();
    }

    private static Stream<String> failuresOf(Events events) {
        return events.failed().stream()
                .map((Event e) -> e.getPayload(TestExecutionResult.class).orElseThrow().getThrowable().orElseThrow())
                .map(Throwable::getMessage);
    }

    private static List<String> failures(Events events) {
        return failuresOf(events).toList();
    }
}
