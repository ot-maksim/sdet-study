package com.study.principles.invariants.retry;

import com.study.principles.invariants.retry.bad.Retrier;
import com.study.principles.invariants.retry.bad.RetryPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.time.Duration.ofMillis;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Documents, as executable facts, how the bad {@link RetryPolicy} gets corrupted.
 * Every test here passes — which is exactly the problem: the class allows all of it.
 */
class BadRetryPolicyTest {

    @Test
    void oneClientChangesTheSharedPolicyForEveryone() {
        var shared = new RetryPolicy();
        var otherTestsRetrier = new Retrier(shared, d -> { });

        shared.getRetryOn().remove(503);              // "just for my test"

        var endpoint = new FlakyEndpoint(503);
        assertThat(otherTestsRetrier.call(endpoint::get)).isEqualTo(503);   // would have been 200
        assertThat(endpoint.calls()).isEqualTo(1);
    }

    @Test
    void callerChangesThePolicyThroughTheSetItPassedIn() {
        var policy = new RetryPolicy();
        Set<Integer> codes = new HashSet<>(Set.of(503));
        policy.setRetryOn(codes);

        codes.clear();                                // aliasing: our state lives in the caller's set

        assertThat(policy.getRetryOn()).isEmpty();
    }

    @Test
    void zeroAttemptsMeansTheRequestIsNeverSent() {
        var policy = new RetryPolicy();
        policy.setMaxAttempts(0);
        var endpoint = new FlakyEndpoint();

        int status = new Retrier(policy, d -> { }).call(endpoint::get);

        assertThat(endpoint.calls()).isZero();
        assertThat(status).isEqualTo(-1);             // a "status" nobody can interpret
    }

    @Test
    void maxDelayBelowInitialMakesBackoffShrink() {
        var policy = new RetryPolicy();
        policy.setMaxAttempts(4);
        policy.setMaxDelayMs(50);                     // initial is 200
        List<Duration> delays = new ArrayList<>();

        new Retrier(policy, delays::add).call(new FlakyEndpoint(503, 503, 503)::get);

        assertThat(delays).containsExactly(ofMillis(200), ofMillis(50), ofMillis(50));
    }
}
