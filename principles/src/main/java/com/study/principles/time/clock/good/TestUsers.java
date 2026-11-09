package com.study.principles.time.clock.good;

import com.study.principles.time.clock.AgeRule;

import java.time.Clock;
import java.time.LocalDate;

/**
 * The same factory, built around the SUT's notion of "today".
 *
 * <h2>Design decisions</h2>
 * <ul>
 *   <li><b>The clock carries the SUT's business zone</b>
 *       ({@code Clock.system(ZoneId.of(config.sutZone()))}), not the runner's default zone. The date
 *       the test computes is the date the SUT will compare with.</li>
 *   <li><b>An instance, not static methods</b>, so the clock is a constructor dependency. In a test
 *       of the factory itself (or in a replay of a failure) pass {@code Clock.fixed(..)}.</li>
 *   <li><b>Boundaries are named</b> ({@code youngestAdult}, {@code oldestMinor}) and derived from one
 *       constant, so the pair always sits exactly on both sides of the rule.</li>
 * </ul>
 */
public final class TestUsers {
    private final Clock sutClock;

    public TestUsers(Clock sutClock) {
        this.sutClock = sutClock;
    }

    /** The SUT's business date. */
    public LocalDate today() {
        return LocalDate.now(sutClock);
    }

    public LocalDate youngestAdultBirthDate() {
        return today().minusYears(AgeRule.ADULT_AGE);
    }

    public LocalDate oldestMinorBirthDate() {
        return youngestAdultBirthDate().plusDays(1);
    }
}
