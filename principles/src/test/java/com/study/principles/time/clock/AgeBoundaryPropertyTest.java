package com.study.principles.time.clock;

import com.study.principles.time.clock.good.TestUsers;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.time.api.Dates;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * For <i>any</i> "today" (leap days, month ends, New Year included) the boundary users are
 * accepted/rejected as their names promise. With {@code LocalDate.now()} inside the factory this
 * property could only ever be checked for the day the suite happens to run.
 */
class AgeBoundaryPropertyTest {

    @Property(tries = 2000)
    void youngestAdultIsAdultAndOldestMinorIsNot(@ForAll("days") LocalDate today) {
        var users = new TestUsers(Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

        assertThat(AgeRule.isAdult(users.youngestAdultBirthDate(), today)).isTrue();
        assertThat(AgeRule.isAdult(users.oldestMinorBirthDate(), today)).isFalse();
    }

    @Provide
    Arbitrary<LocalDate> days() {
        Arbitrary<LocalDate> edgeDays = Arbitraries.of(
                LocalDate.of(2028, 2, 29), LocalDate.of(2027, 2, 28), LocalDate.of(2027, 3, 1),
                LocalDate.of(2026, 12, 31), LocalDate.of(2027, 1, 1));
        return Arbitraries.frequencyOf(
                net.jqwik.api.Tuple.of(1, edgeDays),
                net.jqwik.api.Tuple.of(4, Dates.dates().between(LocalDate.of(2000, 1, 1), LocalDate.of(2100, 12, 31))));
    }
}
