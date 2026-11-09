package com.study.principles.time.clock.bad;

import java.time.LocalDate;

/**
 * Test data factory for sign-up tests: birth dates of an adult and of a minor.
 *
 * <h2>What is wrong</h2>
 * <ul>
 *   <li><b>{@code LocalDate.now()} uses the JVM default time zone</b> of whatever machine runs the
 *       tests: Europe/Moscow on a laptop, UTC on the CI agent. The SUT decides "today" in its own
 *       business zone. For a few hours every day they disagree on the date, and the boundary user
 *       ("18 tomorrow") is accepted or rejected depending on where and when the suite runs.</li>
 *   <li><b>Not reproducible.</b> A failure on 29 February or at 00:30 cannot be replayed on another
 *       day: the data is different every run.</li>
 *   <li><b>Static methods</b> leave no seam for a clock: every caller is bound to the system time.</li>
 * </ul>
 */
public final class TestUsers {

    private TestUsers() {
    }

    /** Born exactly 18 years ago: the youngest user that must be accepted. */
    public static LocalDate youngestAdultBirthDate() {
        return LocalDate.now().minusYears(18);
    }

    /** One day too young: the oldest user that must be rejected. */
    public static LocalDate oldestMinorBirthDate() {
        return LocalDate.now().minusYears(18).plusDays(1);
    }
}
