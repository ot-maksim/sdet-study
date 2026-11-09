package com.study.principles.time.types;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** "Tomorrow at 09:00 in the Berlin office", with the runner's clock in UTC (as on CI). */
class MeetingRequestsTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final LocalTime NINE = LocalTime.of(9, 0);

    @ParameterizedTest(name = "now {0}: expected {1}")
    @CsvSource({
            "2026-11-09T12:00:00Z, 2026-11-10T08:00:00Z",   // winter, +01:00
            "2026-07-01T12:00:00Z, 2026-07-02T07:00:00Z",   // summer, +02:00
            "2026-03-28T12:00:00Z, 2026-03-29T07:00:00Z",   // tomorrow is the spring-forward day
            "2026-11-09T23:30:00Z, 2026-11-11T08:00:00Z",   // already 10 Nov in Berlin, tomorrow is the 11th
    })
    void goodMeetingIsAtNineInBerlinWhateverTheSeasonAndHour(Instant now, Instant expected) {
        var requests = new com.study.principles.time.types.good.MeetingRequests(Clock.fixed(now, ZoneOffset.UTC), BERLIN);

        Instant start = requests.tomorrowAt(NINE);

        assertThat(start).isEqualTo(expected);
        assertThat(start.atZone(BERLIN).toLocalTime()).isEqualTo(NINE);
    }

    @ParameterizedTest(name = "now {0}: bad gives {1}")
    @CsvSource({
            "2026-11-09T12:00:00Z, 2026-11-10T08:00:00Z",   // the day the test was written: correct
            "2026-07-01T12:00:00Z, 2026-07-02T08:00:00Z",   // 10:00 in Berlin
            "2026-11-09T23:30:00Z, 2026-11-10T08:00:00Z",   // "tomorrow" is today in Berlin
    })
    void badMeetingIsRightOnlyInWinterAndInDaytime(Instant now, Instant badResult) {
        var requests = new com.study.principles.time.types.bad.MeetingRequests(Clock.fixed(now, ZoneOffset.UTC));

        assertThat(requests.tomorrowAt(NINE)).isEqualTo(badResult);
    }
}
