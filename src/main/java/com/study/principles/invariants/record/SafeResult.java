package com.study.principles.invariants.record;

import java.util.List;

/**
 * GOOD: a deeply immutable snapshot of failed tests.
 *
 * <h2>Design decision: defensive copy in the compact constructor</h2>
 * {@link List#copyOf} creates an unmodifiable copy and reassigns the constructor parameter before the
 * compiler assigns the field. As a result:
 * <ul>
 *   <li>the caller's original list is disconnected — changing it does not affect the record;</li>
 *   <li>the accessor returns the unmodifiable copy — {@code failedTests().clear()} throws
 *       {@link UnsupportedOperationException};</li>
 *   <li>{@code null} list or {@code null} elements are rejected ({@code copyOf} throws NPE).</li>
 * </ul>
 * Analogues: {@code Set.copyOf}, {@code Map.copyOf}. Arrays and other mutable objects
 * ({@code java.util.Date}, your own mutable classes) need a manual copy, often in the accessor too.
 * <p>
 * Rule: a record is fully immutable only if every component is immutable or defensively copied.
 *
 * @param failedTests names of failed tests (copied on construction)
 * @see ShallowResult the broken version
 */
public record SafeResult(List<String> failedTests) {

    public SafeResult {
        failedTests = List.copyOf(failedTests);
    }

    /** Derived value: computed, never stored, so it cannot get out of sync. */
    public int failedCount() {
        return failedTests.size();
    }
}
