package com.study.principles.time.clock;

import com.study.principles.time.clock.good.TestUsers;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.TimeZone;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class TestUsersTest {
    private static final ZoneId KIRITIMATI = ZoneId.of("Pacific/Kiritimati"); // UTC+14
    private static final ZoneId PAGO_PAGO = ZoneId.of("Pacific/Pago_Pago");   // UTC-11

    /**
     * The same bad factory, the same moment, two runners: the dates always differ because the zones
     * are 25 hours apart. (Mutating the JVM default zone in a test is itself a smell: it is global
     * state and breaks parallel tests. That is exactly the dependency the bad code has.)
     */
    @Test
    void badTestDataDependsOnTheRunnersDefaultZone() {
        LocalDate onRunnerA = withDefaultZone(KIRITIMATI,
                com.study.principles.time.clock.bad.TestUsers::youngestAdultBirthDate);
        LocalDate onRunnerB = withDefaultZone(PAGO_PAGO,
                com.study.principles.time.clock.bad.TestUsers::youngestAdultBirthDate);

        assertThat(onRunnerA).isNotEqualTo(onRunnerB);
    }

    @Test
    void goodTestDataFollowsTheSutClockNotTheRunner() {
        Instant moment = Instant.parse("2026-11-09T23:30:00Z");   // already 10 Nov in Berlin
        var users = new TestUsers(Clock.fixed(moment, ZoneId.of("Europe/Berlin")));

        LocalDate inKiritimati = withDefaultZone(KIRITIMATI, users::youngestAdultBirthDate);
        LocalDate inPagoPago = withDefaultZone(PAGO_PAGO, users::youngestAdultBirthDate);

        assertThat(inKiritimati).isEqualTo(inPagoPago).isEqualTo("2008-11-10");
    }

    @Test
    void goodBoundaryUsersSitOnBothSidesOfTheRuleOnALeapDay() {
        var today = LocalDate.of(2028, 2, 29);
        var users = new TestUsers(Clock.fixed(today.atStartOfDay(ZoneId.of("UTC")).toInstant(), ZoneId.of("UTC")));

        assertThat(users.youngestAdultBirthDate()).isEqualTo("2010-02-28");
        assertThat(users.oldestMinorBirthDate()).isEqualTo("2010-03-01");
        assertThat(AgeRule.isAdult(users.youngestAdultBirthDate(), today)).isTrue();
        assertThat(AgeRule.isAdult(users.oldestMinorBirthDate(), today)).isFalse();
    }

    private static <T> T withDefaultZone(ZoneId zone, Supplier<T> action) {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
        try {
            return action.get();
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
