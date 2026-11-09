package com.study.principles.time.types;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Executable facts behind the "which type" table in the README. Each test is a trap someone
 * stepped into; Europe/Berlin switches to summer time on 2026-03-29 and back on 2026-10-25.
 */
class TimeTypesFactsTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void localDateTimeCannotOrderEventsInTheFallBackHour() {
        Instant first = Instant.parse("2026-10-25T00:30:00Z");
        Instant second = Instant.parse("2026-10-25T01:30:00Z");

        LocalDateTime firstLocal = LocalDateTime.ofInstant(first, BERLIN);
        LocalDateTime secondLocal = LocalDateTime.ofInstant(second, BERLIN);

        assertThat(firstLocal).isEqualTo(secondLocal).isEqualTo("2026-10-25T02:30");  // two events, one "time"
        assertThat(first).isBefore(second);
    }

    @Test
    void aDayIsNotAlways24Hours() {
        ZonedDateTime saturday = ZonedDateTime.of(LocalDate.of(2026, 3, 28), LocalTime.of(9, 0), BERLIN);

        assertThat(saturday.plusDays(1).toLocalTime()).isEqualTo("09:00");            // Period: calendar day
        assertThat(saturday.plus(Duration.ofHours(24)).toLocalTime()).isEqualTo("10:00"); // Duration: 24 h
        assertThat(Duration.between(saturday, saturday.plusDays(1))).isEqualTo(Duration.ofHours(23));
    }

    @Test
    void durationOfDaysIsExactly24HoursEvenOnAZonedDateTime() {
        ZonedDateTime saturday = ZonedDateTime.of(LocalDate.of(2026, 10, 24), LocalTime.of(9, 0), BERLIN);

        assertThat(saturday.plus(Duration.ofDays(1)).toLocalTime()).isEqualTo("08:00");
        assertThat(saturday.plus(Period.ofDays(1)).toLocalTime()).isEqualTo("09:00");
    }

    @Test
    void monthArithmeticClampsToTheLastDayAndDoesNotRoundTrip() {
        LocalDate jan31 = LocalDate.of(2027, 1, 31);

        assertThat(jan31.plusMonths(1)).isEqualTo("2027-02-28");
        assertThat(jan31.plusMonths(1).minusMonths(1)).isEqualTo("2027-01-28");
        assertThat(LocalDate.of(2028, 2, 29).minusYears(1)).isEqualTo("2027-02-28");
    }

    @Test
    void localTimeThatDoesNotExistIsShiftedOrRejected() {
        LocalDateTime inTheGap = LocalDateTime.of(2026, 3, 29, 2, 30);

        assertThat(inTheGap.atZone(BERLIN).toLocalTime()).isEqualTo("03:30");
        assertThatThrownBy(() -> ZonedDateTime.ofStrict(inTheGap, BERLIN.getRules().getOffset(inTheGap), BERLIN))
                .hasMessageContaining("does not exist");
    }

    @Test
    void instantHasNoCalendarFields() {
        Instant now = Instant.parse("2026-11-09T10:00:00Z");

        assertThatThrownBy(() -> now.plus(Period.ofMonths(1)))
                .hasMessageContaining("Months");
        assertThat(now.atZone(BERLIN).plusMonths(1).toInstant()).isEqualTo("2026-12-09T10:00:00Z");
    }
}
