package com.study.principles.invariants.testrun;

import com.study.principles.invariants.testrun.good.TestRun;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests: instead of a few hand-picked examples, jqwik generates hundreds of random
 * operation sequences and checks that the invariants hold after <i>every</i> one of them.
 * <p>
 * An invariant is naturally a "for all sequences, X holds" statement — exactly what property-based
 * testing verifies.
 */
class TestRunPropertyTest {

    enum Op { START, RECORD_PASSED, RECORD_FAILED, FINISH }

    @Property
    void invariantsHoldAfterAnySequenceOfOperations(@ForAll("operations") List<Op> ops) {
        var run = new TestRun(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

        for (Op op : ops) {
            try {
                switch (op) {
                    case START -> run.start();
                    case RECORD_PASSED -> run.record(TestResult.passed("t"));
                    case RECORD_FAILED -> run.record(TestResult.failed("t"));
                    case FINISH -> run.finish();
                }
            } catch (IllegalStateException expected) {
                // Illegal transitions are allowed to fail — but must not corrupt the state
            }
            assertInvariants(run);
        }
    }

    @Property
    void countersMatchRecordedResults(@ForAll List<Boolean> outcomes) {
        var run = new TestRun(Clock.systemUTC());
        run.start();
        outcomes.forEach(ok -> run.record(new TestResult("t", ok)));

        assertThat(run.passed()).isEqualTo(outcomes.stream().filter(b -> b).count());
        assertInvariants(run);
    }

    @Provide
    Arbitrary<List<Op>> operations() {
        return Arbitraries.of(Op.class).list().ofMaxSize(30);
    }

    private static void assertInvariants(TestRun run) {
        // 1. FINISHED <=> duration present
        assertThat(run.duration().isPresent()).isEqualTo(run.status() == Status.FINISHED);
        // 2. finishedAt >= startedAt
        run.duration().ifPresent(d -> assertThat(d.isNegative()).isFalse());
        // 3. passed + failed == results.size()
        assertThat(run.passed() + run.failed()).isEqualTo(run.results().size());
        // 4. nothing is recorded outside RUNNING (results exist only if the run was started)
        if (run.status() == Status.CREATED) {
            assertThat(run.results()).isEmpty();
        }
    }
}
