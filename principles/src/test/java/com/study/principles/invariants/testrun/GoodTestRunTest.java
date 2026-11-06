package com.study.principles.invariants.testrun;

import com.study.principles.invariants.testrun.good.TestRun;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static com.study.principles.invariants.testrun.Status.FINISHED;
import static com.study.principles.invariants.testrun.TestResult.failed;
import static com.study.principles.invariants.testrun.TestResult.passed;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The whole state machine of {@link TestRun} is covered by one happy path plus each forbidden
 * transition. Compare with the bad {@code TestRun}, for which no such list exists: it forbids nothing.
 */
class GoodTestRunTest {
    private final Clock clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);
    private final TestRun run = new TestRun(clock);

    @Test
    void happyPath() {
        run.start();
        run.record(passed("loginTest"));
        run.record(failed("searchTest"));
        run.finish();

        assertThat(run.status()).isEqualTo(FINISHED);
        assertThat(run.passed()).isEqualTo(1);
        assertThat(run.failed()).isEqualTo(1);
        assertThat(run.duration()).isPresent();
    }

    @Test
    void cannotRecordBeforeStart() {
        assertThatThrownBy(() -> run.record(passed("loginTest"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannotFinishBeforeStart() {
        assertThatThrownBy(run::finish).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannotStartTwice() {
        run.start();
        assertThatThrownBy(run::start).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannotRecordAfterFinish() {
        run.start();
        run.finish();
        assertThatThrownBy(() -> run.record(passed("lateTest"))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resultsAreReadOnly() {
        assertThatThrownBy(() -> run.results().add(passed("hackTest")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
