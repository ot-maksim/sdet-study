package com.study.principles.invariants.leak.bad;

import com.study.principles.invariants.leak.TestCase;

import java.util.ArrayList;
import java.util.List;

/**
 * BAD: collects failed test cases, but lets outside code corrupt its state.
 *
 * <h2>Invariant</h2>
 * {@code getFailedCount() == getFailed().size()}
 *
 * <h2>Problems</h2>
 * <ul>
 *   <li>{@link #getFailed()} returns the <b>internal</b> list. Any caller can {@code add},
 *       {@code remove} or {@code clear} it without going through {@link #recordFailure(TestCase)},
 *       so the counter is not updated and the invariant breaks.</li>
 *   <li>The count is a <b>stored derived value</b>: it duplicates information that already exists
 *       (the list size) and must be kept in sync manually. Every duplicated piece of state is
 *       another way to break an invariant.</li>
 * </ul>
 * Typical symptom: a report says "5 failed" while listing only 3 tests — and the place where
 * the list was modified is far away from the place where the mismatch is noticed.
 * <p>
 * Lesson: {@code private} hides the <i>field</i>, it does not protect the <i>object</i> the field
 * points to once the reference has been handed out.
 *
 * @see com.study.principles.invariants.leak.good.TestSuiteResult the fixed version
 */
public final class TestSuiteResult {
    private final List<TestCase> failed = new ArrayList<>();
    private int failedCount;

    /** Records a failure: adds the test case and increments the counter. */
    public void recordFailure(TestCase testCase) {
        failed.add(testCase);
        failedCount++;
    }

    /**
     * Returns the internal list itself — this is the leak.
     *
     * @return the live, mutable internal list
     */
    public List<TestCase> getFailed() {
        return failed;
    }

    /** Returns the stored counter, which may be out of sync with {@link #getFailed()}. */
    public int getFailedCount() {
        return failedCount;
    }
}
