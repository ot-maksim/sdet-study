package com.study.principles.invariants.leak.good;

import com.study.principles.invariants.leak.TestCase;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * GOOD: collects failed test cases and cannot be corrupted from outside.
 *
 * <h2>Invariant</h2>
 * {@code getFailedCount() == getFailed().size()} — and the only way to change the state is
 * {@link #recordFailure(TestCase)}.
 *
 * <h2>Design decisions</h2>
 * <ul>
 *   <li><b>Defensive copy on read.</b> {@link #getFailed()} returns an unmodifiable copy
 *       ({@link List#copyOf}). Callers can read and filter it, but any attempt to modify it throws
 *       {@link UnsupportedOperationException}, and the internal list is never shared.</li>
 *   <li><b>Derive, don't store.</b> The count is computed from the list instead of being kept in a
 *       separate field. A value that is not stored cannot get out of sync — the invariant holds by
 *       construction, not by discipline.</li>
 *   <li><b>Validate input.</b> {@code null} is rejected at the boundary, so it cannot surface later
 *       as an unrelated {@link NullPointerException} in a reporter.</li>
 * </ul>
 * If a caller needs a filtered view (e.g. without flaky tests), it builds its own list from the
 * copy; the source of truth stays intact.
 *
 * @see com.study.principles.invariants.leak.bad.TestSuiteResult the broken version
 */
public final class TestSuiteResult {
    private final List<TestCase> failed = new ArrayList<>();

    /**
     * Records a failure.
     *
     * @param testCase failed test case, must not be {@code null}
     * @throws NullPointerException if {@code testCase} is {@code null}
     */
    public void recordFailure(TestCase testCase) {
        failed.add(Objects.requireNonNull(testCase, "testCase"));
    }

    /**
     * Returns a read-only snapshot of failed test cases.
     *
     * @return unmodifiable copy; later failures are not reflected in it
     */
    public List<TestCase> getFailed() {
        return List.copyOf(failed);
    }

    /** Number of failures, always consistent with {@link #getFailed()}. */
    public int getFailedCount() {
        return failed.size();
    }
}
