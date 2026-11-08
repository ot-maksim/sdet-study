package com.study.principles.invariants.retry.bad;

import com.study.principles.invariants.retry.Sleeper;

import java.time.Duration;
import java.util.function.IntSupplier;

/**
 * BAD: a client of the bad {@link RetryPolicy}. The retry rules (backoff, cap, what is retryable)
 * live here, in the caller, and the policy's values are trusted blindly.
 * Every other client that uses the policy would have to repeat this logic.
 */
public final class Retrier {
    private final RetryPolicy policy;
    private final Sleeper sleeper;

    public Retrier(RetryPolicy policy, Sleeper sleeper) {
        this.policy = policy;
        this.sleeper = sleeper;
    }

    /** Sends the request, retrying while the status is retryable; returns the last status. */
    public int call(IntSupplier request) {
        int status = -1;                                  // "no response" — what should this even be?
        long delayMs = policy.getInitialDelayMs();
        for (int attempt = 1; attempt <= policy.getMaxAttempts(); attempt++) {
            status = request.getAsInt();
            if (!policy.getRetryOn().contains(status) || attempt == policy.getMaxAttempts()) {
                return status;
            }
            sleeper.sleep(Duration.ofMillis(delayMs));
            delayMs = Math.min(delayMs * 2, policy.getMaxDelayMs());
        }
        return status;                                    // reached only if maxAttempts < 1: nothing was sent
    }
}
