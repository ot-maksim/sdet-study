package com.study.principles.invariants.record;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Boundary value analysis around the invariant {@code from <= to}. */
class DateRangeTest {
    private static final LocalDate MAY_1 = LocalDate.of(2026, 5, 1);
    private static final LocalDate MAY_2 = LocalDate.of(2026, 5, 2);

    @Test
    void acceptsSameDay() {          // boundary: from == to
        assertThatCode(() -> new DateRange(MAY_1, MAY_1)).doesNotThrowAnyException();
        assertThat(new DateRange(MAY_1, MAY_1).days()).isEqualTo(1);
    }

    @Test
    void acceptsAdjacentDays() {     // just inside: from = to - 1
        assertThat(new DateRange(MAY_1, MAY_2).days()).isEqualTo(2);
    }

    @Test
    void rejectsReversedRange() {    // just outside: from = to + 1
        assertThatThrownBy(() -> new DateRange(MAY_2, MAY_1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is after");
    }

    @Test
    void rejectsNullBounds() {
        assertThatThrownBy(() -> new DateRange(null, MAY_1)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new DateRange(MAY_1, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void containsIncludesBounds() {
        var range = new DateRange(MAY_1, MAY_2);
        assertThat(range.contains(MAY_1)).isTrue();
        assertThat(range.contains(MAY_2)).isTrue();
        assertThat(range.contains(MAY_2.plusDays(1))).isFalse();
    }
}
