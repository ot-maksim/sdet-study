package com.study.principles.invariants.retry;

import com.study.principles.invariants.retry.good.Retrier;
import com.study.principles.invariants.retry.good.RetryPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.time.Duration.ofMillis;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The good {@link RetryPolicy}: it cannot be built invalid, cannot be changed from outside, and its
 * clients need no defensive code.
 */
class GoodRetryPolicyTest {

    private static final Set<Integer> GATEWAY_ERRORS = Set.of(502, 503, 504);

    // --- invariants are enforced at construction ---

    static Stream<Arguments> invalidPolicies() {
        return Stream.of(
                invalid("zero attempts", () -> new RetryPolicy(0, ofMillis(200), ofMillis(1000), GATEWAY_ERRORS)),
                invalid("negative attempts", () -> new RetryPolicy(-1, ofMillis(200), ofMillis(1000), GATEWAY_ERRORS)),
                invalid("zero initial delay", () -> new RetryPolicy(3, Duration.ZERO, ofMillis(1000), GATEWAY_ERRORS)),
                invalid("negative initial delay", () -> new RetryPolicy(3, ofMillis(-1), ofMillis(1000), GATEWAY_ERRORS)),
                invalid("max < initial", () -> new RetryPolicy(3, ofMillis(200), ofMillis(100), GATEWAY_ERRORS)),
                invalid("no retryable codes", () -> new RetryPolicy(3, ofMillis(200), ofMillis(1000), Set.of())),
                invalid("retry on success", () -> new RetryPolicy(3, ofMillis(200), ofMillis(1000), Set.of(200, 503))),
                invalid("not an HTTP code", () -> new RetryPolicy(3, ofMillis(200), ofMillis(1000), Set.of(1503))),
                invalid("with-copy is checked too", () -> RetryPolicy.defaults().withMaxAttempts(0)));
    }

    private static Arguments invalid(String name, Supplier<RetryPolicy> build) {
        return Arguments.of(name, build);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPolicies")
    void invalidPolicyCannotBeBuilt(String name, Supplier<RetryPolicy> build) {
        assertThatThrownBy(build::get).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullsAreRejected() {
        assertThatThrownBy(() -> new RetryPolicy(3, null, ofMillis(1000), GATEWAY_ERRORS))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RetryPolicy(3, ofMillis(200), ofMillis(1000), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RetryPolicy(3, ofMillis(200), ofMillis(1000), new HashSet<>(Arrays.asList(503, null))))
                .isInstanceOf(NullPointerException.class);
    }

    // --- invariants cannot be broken from outside ---

    @Test
    void callerKeepsNoReferenceToTheInternalSet() {
        Set<Integer> callers = new HashSet<>(GATEWAY_ERRORS);
        var policy = new RetryPolicy(3, ofMillis(200), ofMillis(1000), callers);

        callers.clear();

        assertThat(policy.retryOn()).containsExactlyInAnyOrder(502, 503, 504);
    }

    @Test
    void accessorDoesNotExposeAModifiableSet() {
        var policy = RetryPolicy.defaults();

        assertThatThrownBy(() -> policy.retryOn().remove(503)).isInstanceOf(UnsupportedOperationException.class);
        assertThat(policy.retries(503)).isTrue();
    }

    @Test
    void withCopyLeavesTheOriginalUnchanged() {
        var shared = RetryPolicy.defaults();

        var local = shared.withRetryOn(Set.of(502));

        assertThat(local.retries(503)).isFalse();
        assertThat(shared).isEqualTo(RetryPolicy.defaults());
    }

    // --- behaviour: fields are trusted, only arguments are checked ---

    @Test
    void backoffDoublesAndIsCapped() {
        var policy = new RetryPolicy(6, ofMillis(200), ofMillis(1000), GATEWAY_ERRORS);

        assertThat(List.of(2, 3, 4, 5, 6)).map(policy::delayBefore)
                .containsExactly(ofMillis(200), ofMillis(400), ofMillis(800), ofMillis(1000), ofMillis(1000));
    }

    @Test
    void delayBeforeChecksItsArgument() {
        var policy = RetryPolicy.defaults();   // 3 attempts

        assertThatThrownBy(() -> policy.delayBefore(1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> policy.delayBefore(4)).isInstanceOf(IllegalArgumentException.class);
    }

    // --- the client needs no defensive code ---

    @Test
    void retriesUntilSuccess() {
        var endpoint = new FlakyEndpoint(503, 502);
        List<Duration> delays = new ArrayList<>();

        int status = new Retrier(RetryPolicy.defaults(), delays::add).call(endpoint::get);

        assertThat(status).isEqualTo(200);
        assertThat(endpoint.calls()).isEqualTo(3);
        assertThat(delays).containsExactly(ofMillis(200), ofMillis(400));
    }

    @Test
    void givesUpAfterMaxAttempts() {
        var endpoint = new FlakyEndpoint(503, 503, 503, 503);

        int status = new Retrier(RetryPolicy.defaults(), d -> { }).call(endpoint::get);

        assertThat(status).isEqualTo(503);
        assertThat(endpoint.calls()).isEqualTo(3);
    }

    @Test
    void doesNotRetryNonRetryableStatus() {
        var endpoint = new FlakyEndpoint(404);

        int status = new Retrier(RetryPolicy.defaults(), d -> { }).call(endpoint::get);

        assertThat(status).isEqualTo(404);
        assertThat(endpoint.calls()).isEqualTo(1);
    }
}
