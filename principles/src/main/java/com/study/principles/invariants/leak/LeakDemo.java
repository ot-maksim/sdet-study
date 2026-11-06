package com.study.principles.invariants.leak;

/**
 * Runs the same scenario against the bad and the good {@code TestSuiteResult}:
 * a reporter "just filters out flaky tests" through the getter.
 */
public class LeakDemo {

    public static void main(String[] args) {
        System.out.println("=== BAD: internal list leaks through the getter ===");
        var bad = new com.study.principles.invariants.leak.bad.TestSuiteResult();
        fillBad(bad);
        bad.getFailed().removeIf(TestCase::flaky);   // reporter modifies the source of truth
        System.out.println("  list size = " + bad.getFailed().size());
        System.out.println("  counter   = " + bad.getFailedCount() + "   <-- invariant broken");

        System.out.println("\n=== GOOD: getter returns an unmodifiable copy ===");
        var good = new com.study.principles.invariants.leak.good.TestSuiteResult();
        fillGood(good);
        try {
            good.getFailed().removeIf(TestCase::flaky);
        } catch (UnsupportedOperationException e) {
            System.out.println("  modification rejected: " + e.getClass().getSimpleName());
        }
        // The right way: build your own filtered view from the copy
        long stable = good.getFailed().stream().filter(tc -> !tc.flaky()).count();
        System.out.println("  stable failures (own view) = " + stable);
        System.out.println("  list size = " + good.getFailed().size());
        System.out.println("  counter   = " + good.getFailedCount() + "   <-- invariant holds");
    }

    private static void fillBad(com.study.principles.invariants.leak.bad.TestSuiteResult r) {
        sample().forEach(r::recordFailure);
    }

    private static void fillGood(com.study.principles.invariants.leak.good.TestSuiteResult r) {
        sample().forEach(r::recordFailure);
    }

    private static java.util.List<TestCase> sample() {
        return java.util.List.of(
                new TestCase("loginTest", false),
                new TestCase("searchTest", true),
                new TestCase("checkoutTest", false),
                new TestCase("profileTest", true),
                new TestCase("logoutTest", false));
    }
}
