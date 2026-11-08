package com.study.principles.invariants.retry.bad;

import java.util.HashSet;
import java.util.Set;

/**
 * BAD: a retry policy as a bag of raw values with a getter and a setter per field.
 *
 * <h2>Intended invariants</h2>
 * <ol>
 *   <li>{@code maxAttempts >= 1}</li>
 *   <li>{@code 0 < initialDelay <= maxDelay}</li>
 *   <li>{@code retryOn} is non-empty, contains only error codes 400..599 and cannot change behind
 *       the policy's back</li>
 * </ol>
 *
 * <h2>Problems — the class guarantees none of them</h2>
 * <table border="1">
 *   <caption>Ways to break the invariants</caption>
 *   <tr><th>Action</th><th>Broken invariant</th><th>Symptom</th></tr>
 *   <tr><td>{@code setMaxAttempts(0)}</td><td>1</td><td>the request is never sent</td></tr>
 *   <tr><td>{@code setMaxDelayMs(100)} with initial 200</td><td>2</td><td>backoff shrinks</td></tr>
 *   <tr><td>{@code setInitialDelayMs(-1)}</td><td>2</td><td>{@code Thread.sleep} throws far from the cause</td></tr>
 *   <tr><td>{@code getRetryOn().remove(503)}</td><td>3</td><td>every client sharing the policy stops retrying</td></tr>
 *   <tr><td>{@code setRetryOn(callersSet)}, caller changes its set later</td><td>3</td><td>same, via aliasing</td></tr>
 *   <tr><td>{@code setRetryOn(Set.of(200))}</td><td>3</td><td>successful responses are retried</td></tr>
 * </table>
 *
 * <ul>
 *   <li><b>Rules live in the callers.</b> Every retrier has to re-check the values — or, as in
 *       {@link Retrier}, silently trusts them.</li>
 *   <li><b>Units in names, not types.</b> {@code long ...Ms} — nothing stops someone passing seconds.</li>
 *   <li><b>Shared mutable state.</b> A policy is usually a constant in a base class; one test that
 *       "temporarily" tweaks it changes it for all tests that run afterwards (or in parallel).</li>
 * </ul>
 *
 * @see com.study.principles.invariants.retry.good.RetryPolicy the fixed version
 */
public class RetryPolicy {
    private int maxAttempts = 3;
    private long initialDelayMs = 200;
    private long maxDelayMs = 5_000;
    private Set<Integer> retryOn = new HashSet<>(Set.of(502, 503, 504));

    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }

    public long getInitialDelayMs() { return initialDelayMs; }
    public void setInitialDelayMs(long initialDelayMs) { this.initialDelayMs = initialDelayMs; }

    public long getMaxDelayMs() { return maxDelayMs; }
    public void setMaxDelayMs(long maxDelayMs) { this.maxDelayMs = maxDelayMs; }

    /** Returns the live internal set — callers can modify it freely. */
    public Set<Integer> getRetryOn() { return retryOn; }

    /** Stores the caller's set as is — the caller keeps a reference to our state. */
    public void setRetryOn(Set<Integer> retryOn) { this.retryOn = retryOn; }
}
