package com.study.principles.invariants.record;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * An inclusive date range, e.g. a report filter in a test.
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@code from} and {@code to} are not {@code null};</li>
 *   <li>{@code from <= to} (a single-day range {@code from == to} is valid).</li>
 * </ul>
 *
 * <h2>Design decisions</h2>
 * <ul>
 *   <li><b>Immutability + validation at creation = invariant always holds.</b> The compact
 *       constructor checks the rules once; since nobody can change the object afterwards, there is
 *       no other place where the rules need to be re-checked.</li>
 *   <li><b>Fail fast.</b> An invalid range fails where it was created, with a clear message, instead
 *       of producing an empty report three layers later.</li>
 *   <li><b>Components are immutable</b> ({@link LocalDate}), so the record is deeply immutable without
 *       any defensive copies.</li>
 * </ul>
 *
 * @param from first day, inclusive
 * @param to   last day, inclusive
 */
public record DateRange(LocalDate from, LocalDate to) {

    /**
     * Compact constructor: no parameter list, the fields are assigned by the compiler after this block.
     *
     * @throws NullPointerException     if any bound is {@code null}
     * @throws IllegalArgumentException if {@code from} is after {@code to}
     */
    public DateRange {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from (" + from + ") is after to (" + to + ")");
        }
    }

    /** Number of days in the range; always {@code >= 1} thanks to the invariant. */
    public long days() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }

    /** Whether {@code date} falls into the range (bounds included). */
    public boolean contains(LocalDate date) {
        return !date.isBefore(from) && !date.isAfter(to);
    }
}
