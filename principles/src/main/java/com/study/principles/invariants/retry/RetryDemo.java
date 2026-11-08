package com.study.principles.invariants.retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Two "tests" share one retry policy, as they would through a constant in a base test class.
 * Test A wants no retries on 503 for its own check; test B runs afterwards against a flaky endpoint.
 */
public class RetryDemo {

    public static void main(String[] args) {
        bad();
        good();
        failFast();
    }

    private static void bad() {
        System.out.println("=== BAD: mutable policy, live set behind the getter ===");
        var shared = new com.study.principles.invariants.retry.bad.RetryPolicy();

        // test A: "just for this test" — through the getter, i.e. for everybody
        shared.getRetryOn().remove(503);
        System.out.println("  test A: removed 503 from the shared policy");

        // test B: the endpoint is flaky once, a retry would have saved it
        var endpoint = new FlakyEndpoint(503);
        int status = new com.study.principles.invariants.retry.bad.Retrier(shared, d -> { }).call(endpoint::get);
        System.out.println("  test B: status " + status + " after " + endpoint.calls() + " call(s)   <-- flaky failure");

        shared.setMaxAttempts(0);
        var silent = new FlakyEndpoint();
        status = new com.study.principles.invariants.retry.bad.Retrier(shared, d -> { }).call(silent::get);
        System.out.println("  maxAttempts=0: status " + status + ", calls sent = " + silent.calls()
                + "   <-- the request never left");

        shared.setMaxAttempts(5);
        shared.setRetryOn(Set.of(503));
        shared.setMaxDelayMs(50);
        List<Duration> delays = new ArrayList<>();
        new com.study.principles.invariants.retry.bad.Retrier(shared, delays::add).call(new FlakyEndpoint(503, 503, 503, 503)::get);
        System.out.println("  maxDelay < initialDelay: delays " + delays + "   <-- backoff shrinks");
    }

    private static void good() {
        System.out.println("\n=== GOOD: immutable policy, local copies ===");
        var shared = com.study.principles.invariants.retry.good.RetryPolicy.defaults();

        try {
            shared.retryOn().remove(503);
        } catch (UnsupportedOperationException e) {
            System.out.println("  test A: modifying the shared policy rejected: " + e.getClass().getSimpleName());
        }
        var forTestA = shared.withRetryOn(Set.of(502, 504));    // the right way: a local copy
        System.out.println("  test A: own policy retries " + forTestA.retryOn() + ", shared still " + shared.retryOn());

        var endpoint = new FlakyEndpoint(503);
        List<Duration> delays = new ArrayList<>();
        int status = new com.study.principles.invariants.retry.good.Retrier(shared, delays::add).call(endpoint::get);
        System.out.println("  test B: status " + status + " after " + endpoint.calls() + " call(s), delays " + delays);
    }

    private static void failFast() {
        System.out.println("\n=== GOOD: an invalid policy cannot be built ===");
        var defaults = com.study.principles.invariants.retry.good.RetryPolicy.defaults();
        tryBuild(() -> defaults.withMaxAttempts(0));
        tryBuild(() -> defaults.withMaxDelay(Duration.ofMillis(50)));
        tryBuild(() -> defaults.withRetryOn(Set.of(200)));
    }

    private static void tryBuild(Runnable build) {
        try {
            build.run();
        } catch (IllegalArgumentException e) {
            System.out.println("  rejected: " + e.getMessage());
        }
    }
}
