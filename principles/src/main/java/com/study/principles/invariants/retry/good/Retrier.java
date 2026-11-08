package com.study.principles.invariants.retry.good;

import com.study.principles.invariants.retry.Sleeper;

import java.util.Objects;
import java.util.function.IntSupplier;

/**
 * GOOD: a client of the good {@link RetryPolicy}. Compare with the bad {@code Retrier}: no guard for
 * "zero attempts", no "no response" sentinel, no backoff arithmetic. The invariants of the policy
 * guarantee at least one request and a sane schedule, so the client only expresses intent.
 */
public final class Retrier {
    private final RetryPolicy policy;
    private final Sleeper sleeper;

    public Retrier(RetryPolicy policy, Sleeper sleeper) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    /** Sends the request, retrying while the status is retryable; returns the last status. */
    public int call(IntSupplier request) {
        for (int attempt = 1; ; attempt++) {
            int status = request.getAsInt();
            if (!policy.retries(status) || attempt == policy.maxAttempts()) {
                return status;
            }
            sleeper.sleep(policy.delayBefore(attempt + 1));
        }
    }
}
