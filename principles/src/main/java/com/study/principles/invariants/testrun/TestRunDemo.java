package com.study.principles.invariants.testrun;

import java.time.Clock;
import java.time.Instant;

/**
 * Plays a few "careless caller" scenarios against both versions of {@code TestRun}.
 */
public class TestRunDemo {

    public static void main(String[] args) {
        System.out.println("=== BAD: setters accept any sequence ===");
        var bad = new com.study.principles.invariants.testrun.bad.TestRun();
        bad.setStatus(Status.FINISHED);                              // finished, but never started
        System.out.println("  status=" + bad.getStatus() + ", finishedAt=" + bad.getFinishedAt()
                + "   <-- invariant 1 broken");

        bad.setStartedAt(Instant.parse("2026-01-01T10:00:00Z"));
        bad.setFinishedAt(Instant.parse("2026-01-01T09:00:00Z"));    // finished before started
        System.out.println("  startedAt=" + bad.getStartedAt() + ", finishedAt=" + bad.getFinishedAt()
                + "   <-- invariant 2 broken");

        bad.getResults().add(TestResult.passed("loginTest"));         // counter not updated
        System.out.println("  results=" + bad.getResults().size() + ", passed+failed="
                + (bad.getPassed() + bad.getFailed()) + "   <-- invariant 3 broken");

        System.out.println("\n=== GOOD: illegal transitions are rejected ===");
        var good = new com.study.principles.invariants.testrun.good.TestRun(Clock.systemUTC());
        attempt("finish() before start()", good::finish);
        attempt("record() before start()", () -> good.record(TestResult.passed("loginTest")));

        good.start();
        good.record(TestResult.passed("loginTest"));
        good.record(TestResult.failed("searchTest"));
        good.finish();
        System.out.println("  happy path: status=" + good.status() + ", passed=" + good.passed()
                + ", failed=" + good.failed() + ", duration present=" + good.duration().isPresent());

        attempt("record() after finish()", () -> good.record(TestResult.passed("lateTest")));
        attempt("results().add(..)", () -> good.results().add(TestResult.passed("hackTest")));
    }

    private static void attempt(String what, Runnable action) {
        try {
            action.run();
            System.out.println("  " + what + ": allowed?!");
        } catch (RuntimeException e) {
            System.out.println("  " + what + ": rejected (" + e.getClass().getSimpleName()
                    + (e.getMessage() != null ? ": " + e.getMessage() : "") + ")");
        }
    }
}
