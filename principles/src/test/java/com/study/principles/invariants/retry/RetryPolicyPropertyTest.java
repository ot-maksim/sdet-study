package com.study.principles.invariants.retry;

import com.study.principles.invariants.retry.good.Retrier;
import com.study.principles.invariants.retry.good.RetryPolicy;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.constraints.IntRange;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Property-based tests: for ANY valid policy and ANY sequence of responses the guarantees hold.
 * The invariants of the policy are what make these "for all" statements true without a single
 * guard in {@link Retrier}.
 */
class RetryPolicyPropertyTest {

    @Property
    void backoffIsNonDecreasingAndWithinBounds(@ForAll("policies") RetryPolicy policy) {
        Duration previous = Duration.ZERO;
        for (int attempt = 2; attempt <= policy.maxAttempts(); attempt++) {
            Duration delay = policy.delayBefore(attempt);
            assertThat(delay).isBetween(policy.initialDelay(), policy.maxDelay());
            assertThat(delay).isGreaterThanOrEqualTo(previous);
            previous = delay;
        }
    }

    @Property
    void retrierSendsAtLeastOneAndAtMostMaxAttemptsRequests(
            @ForAll("policies") RetryPolicy policy,
            @ForAll List<@IntRange(min = 200, max = 599) Integer> responses) {
        var endpoint = new FlakyEndpoint(responses.toArray(Integer[]::new));
        List<Duration> delays = new ArrayList<>();

        int status = new Retrier(policy, delays::add).call(endpoint::get);

        assertThat(endpoint.calls()).isBetween(1, policy.maxAttempts());
        assertThat(delays).hasSize(endpoint.calls() - 1);
        if (policy.retries(status)) {
            assertThat(endpoint.calls()).isEqualTo(policy.maxAttempts());   // gave up only when out of attempts
        }
    }

    @Property
    void nonPositiveAttemptsAreAlwaysRejected(@ForAll @IntRange(min = Integer.MIN_VALUE, max = 0) int attempts) {
        assertThatThrownBy(() -> RetryPolicy.defaults().withMaxAttempts(attempts))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Provide
    Arbitrary<RetryPolicy> policies() {
        Arbitrary<Integer> attempts = Arbitraries.integers().between(1, 10);
        Arbitrary<Long> initialMs = Arbitraries.longs().between(1, 10_000);
        Arbitrary<Long> extraMs = Arbitraries.longs().between(0, 100_000);
        Arbitrary<Set<Integer>> codes = Arbitraries.integers().between(400, 599).set().ofMinSize(1).ofMaxSize(5);
        return Combinators.combine(attempts, initialMs, extraMs, codes)
                .as((a, initial, extra, c) ->
                        new RetryPolicy(a, Duration.ofMillis(initial), Duration.ofMillis(initial + extra), c));
    }
}
