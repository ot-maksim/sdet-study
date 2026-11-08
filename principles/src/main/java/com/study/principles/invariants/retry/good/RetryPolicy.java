package com.study.principles.invariants.retry.good;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/**
 * GOOD: an immutable retry policy that cannot exist in an invalid state.
 *
 * <h2>Invariants (guaranteed by the class)</h2>
 * <ol>
 *   <li>{@code maxAttempts >= 1};</li>
 *   <li>{@code 0 < initialDelay <= maxDelay};</li>
 *   <li>{@code retryOn} is non-empty, contains only error codes 400..599 and is unmodifiable.</li>
 * </ol>
 *
 * <h2>Design decisions</h2>
 * <ul>
 *   <li><b>One entry point.</b> Every instance — including the {@code with...} copies — goes through
 *       the compact constructor, so there is no way around the checks.</li>
 *   <li><b>Fail fast.</b> An invalid policy is rejected where it is built (test setup), not in the
 *       middle of a retry loop.</li>
 *   <li><b>Defensive copy.</b> {@code Set.copyOf} detaches the set from the caller, makes it immutable
 *       and rejects nulls. Since the stored set is immutable, the accessor needs no copy on read.
 *       Without it the record would be only <i>shallowly</i> immutable
 *       (see {@code invariants.record}).</li>
 *   <li><b>Types instead of comments.</b> {@link Duration} carries its unit; {@code long ms} does not.</li>
 *   <li><b>Behaviour next to the data.</b> {@link #retries(int)} and {@link #delayBefore(int)} own the
 *       rules, so clients ({@link Retrier}) contain no checks and no backoff arithmetic.</li>
 *   <li><b>Preconditions vs invariants.</b> {@link #delayBefore(int)} validates its <i>argument</i>
 *       but trusts its <i>fields</i>: they were validated once, at construction.</li>
 * </ul>
 *
 * <h2>Why a record here (unlike {@code encapsulation.good.TestConfig})</h2>
 * The components <i>are</i> the policy's public meaning — there is no hidden representation to
 * protect, only rules. A record gives {@code equals}/{@code hashCode}/{@code toString} for free;
 * the compact constructor keeps the rules.
 *
 * @see com.study.principles.invariants.retry.bad.RetryPolicy the broken version
 */
public record RetryPolicy(int maxAttempts, Duration initialDelay, Duration maxDelay, Set<Integer> retryOn) {

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1, was " + maxAttempts);
        }
        Objects.requireNonNull(initialDelay, "initialDelay");
        Objects.requireNonNull(maxDelay, "maxDelay");
        if (initialDelay.isZero() || initialDelay.isNegative()) {
            throw new IllegalArgumentException("initialDelay must be > 0, was " + initialDelay);
        }
        if (maxDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException(
                    "maxDelay (" + maxDelay + ") must be >= initialDelay (" + initialDelay + ")");
        }
        retryOn = Set.copyOf(Objects.requireNonNull(retryOn, "retryOn"));
        if (retryOn.isEmpty() || retryOn.stream().anyMatch(code -> code < 400 || code > 599)) {
            throw new IllegalArgumentException("retryOn must be non-empty error codes 400..599, was " + retryOn);
        }
    }

    /** 3 attempts, 200 ms doubling up to 5 s, retry on 502/503/504. */
    public static RetryPolicy defaults() {
        return new RetryPolicy(3, Duration.ofMillis(200), Duration.ofSeconds(5), Set.of(502, 503, 504));
    }

    /** "Modification" = a new policy that passes the same checks; this one is unchanged. */
    public RetryPolicy withMaxAttempts(int maxAttempts) {
        return new RetryPolicy(maxAttempts, initialDelay, maxDelay, retryOn);
    }

    public RetryPolicy withMaxDelay(Duration maxDelay) {
        return new RetryPolicy(maxAttempts, initialDelay, maxDelay, retryOn);
    }

    public RetryPolicy withRetryOn(Set<Integer> retryOn) {
        return new RetryPolicy(maxAttempts, initialDelay, maxDelay, retryOn);
    }

    /** Whether a response with this status should be retried. */
    public boolean retries(int status) {
        return retryOn.contains(status);
    }

    /**
     * Pause before the given attempt: exponential backoff from {@code initialDelay}, capped at
     * {@code maxDelay}.
     *
     * @param attempt attempt number, {@code 2..maxAttempts} (the first attempt has no pause)
     * @throws IllegalArgumentException if {@code attempt} is out of range (a precondition)
     */
    public Duration delayBefore(int attempt) {
        if (attempt < 2 || attempt > maxAttempts) {
            throw new IllegalArgumentException("attempt must be in 2.." + maxAttempts + ", was " + attempt);
        }
        Duration delay = initialDelay;
        for (int i = 2; i < attempt && delay.compareTo(maxDelay) < 0; i++) {
            delay = delay.multipliedBy(2);
        }
        return delay.compareTo(maxDelay) > 0 ? maxDelay : delay;
    }
}
