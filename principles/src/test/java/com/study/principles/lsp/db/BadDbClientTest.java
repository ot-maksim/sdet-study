package com.study.principles.lsp.db;

import com.study.principles.lsp.db.bad.DbClient;
import com.study.principles.lsp.db.bad.ReadOnlyDbClient;
import com.study.principles.lsp.db.bad.SilentReadOnlyDbClient;
import com.study.principles.lsp.db.bad.TestDataCleaner;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.testkit.engine.EventConditions.finishedWithFailure;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.instanceOf;

class BadDbClientTest {

    @Nested
    class BaseClientHonoursItsOwnContract implements DbClientContract {
        @Override
        public DbClient client(FakeDatabase db) {
            return new DbClient(db);
        }
    }

    /** The contract run against the subclasses — the LSP check. Input classes at the bottom. */
    @Nested
    class ContractAgainstSubclasses {

        @Test
        void readOnlyFailsLoudlyOnBothWrites() {
            Events events = run(ReadOnlyContract.class);

            events.assertStatistics(stats -> stats.started(3).succeeded(1).failed(2));
            events.failed().assertThatEvents()
                    .haveExactly(2, finishedWithFailure(instanceOf(UnsupportedOperationException.class)));
        }

        @Test
        void silentReadOnlyFailsOnlyWhenSomebodyChecksTheEffect() {
            Events events = run(SilentContract.class);

            events.assertStatistics(stats -> stats.started(3).succeeded(1).failed(2));
            events.failed().assertThatEvents()
                    .haveExactly(2, finishedWithFailure(instanceOf(AssertionError.class)));   // no exception, wrong state
        }
    }

    @Nested
    class InTheFramework {

        private final FakeDatabase stand = new FakeDatabase();

        @Test
        void cleanupWithTheLoudClientFailsAfterTheTestItselfPassed() {
            stand.seed("autotest-1@shop.test");
            TestDataCleaner cleaner = new TestDataCleaner(new ReadOnlyDbClient(stand));   // compiles: it IS-A DbClient

            assertThatThrownBy(() -> cleaner.cleanUp("autotest-"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        void cleanupWithTheSilentClientReportsSuccessAndLeavesTheData() {
            stand.seed("autotest-1@shop.test");
            TestDataCleaner cleaner = new TestDataCleaner(new SilentReadOnlyDbClient(stand));

            assertThat(cleaner.cleanUp("autotest-")).isZero();
            assertThat(stand.select("autotest-")).as("the next test inherits this").containsExactly("autotest-1@shop.test");
        }

        @Test
        void theInstanceofPatchStopsTheExceptionNotTheProblem() {
            stand.seed("autotest-1@shop.test");
            var loud = new com.study.principles.lsp.db.bad.patched.TestDataCleaner(new ReadOnlyDbClient(stand));
            var silent = new com.study.principles.lsp.db.bad.patched.TestDataCleaner(new SilentReadOnlyDbClient(stand));

            assertThat(loud.cleanUp("autotest-")).isZero();
            assertThat(silent.cleanUp("autotest-")).isZero();   // not even recognised by the check
            assertThat(stand.select("autotest-")).containsExactly("autotest-1@shop.test");
        }
    }

    private static Events run(Class<?> testClass) {
        return EngineTestKit.engine("junit-jupiter").selectors(selectClass(testClass)).execute().testEvents();
    }

    // ---- input classes ---------------------------------------------------------------------------

    static class ReadOnlyContract implements DbClientContract {
        @Override
        public DbClient client(FakeDatabase db) {
            return new ReadOnlyDbClient(db);
        }
    }

    static class SilentContract implements DbClientContract {
        @Override
        public DbClient client(FakeDatabase db) {
            return new SilentReadOnlyDbClient(db);
        }
    }
}
