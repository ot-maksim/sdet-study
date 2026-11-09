package com.study.principles.time.clock;

import java.time.LocalDate;
import java.time.Period;

/**
 * Stand-in for the SUT's sign-up rule: a user must be at least 18 on the SUT's business date.
 *
 * <p>Note the signature: the rule takes {@code today} as a parameter instead of calling
 * {@code LocalDate.now()} inside. A pure function of its inputs is the easiest thing to test,
 * and the caller decides which "today" (which zone, which clock) applies.
 */
public final class AgeRule {
    public static final int ADULT_AGE = 18;

    private AgeRule() {
    }

    public static boolean isAdult(LocalDate birthDate, LocalDate today) {
        return Period.between(birthDate, today).getYears() >= ADULT_AGE;
    }
}
