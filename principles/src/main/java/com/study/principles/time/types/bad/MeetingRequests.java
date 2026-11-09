package com.study.principles.time.types.bad;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

/**
 * Test data for the booking API: "a meeting tomorrow at 09:00 in the Berlin office", sent as an
 * {@link Instant}. The clock is injected here on purpose, so that the only difference from
 * {@code good} is the choice of time types.
 *
 * <h2>What is wrong</h2>
 * <ul>
 *   <li><b>An offset is not a zone.</b> {@code ZoneOffset.ofHours(1)} is Berlin only in winter. From
 *       the last Sunday of March to the last Sunday of October Berlin is {@code +02:00}, and the
 *       meeting lands at 10:00 local. The test was written in November and passed.</li>
 *   <li><b>"Tomorrow" in the wrong zone.</b> {@code LocalDate.now(clock)} uses the clock's zone
 *       (UTC on CI). Between 00:00 and 01:00/02:00 Berlin time it is still yesterday in UTC, so
 *       "tomorrow" is actually today.</li>
 *   <li><b>{@code LocalDateTime} in the middle</b> (from {@code atTime}) has no zone at all; the
 *       zone is bolted on at the end with a guess.</li>
 * </ul>
 */
public final class MeetingRequests {
    private static final ZoneOffset BERLIN = ZoneOffset.ofHours(1);

    private final Clock clock;

    public MeetingRequests(Clock clock) {
        this.clock = clock;
    }

    public Instant tomorrowAt(LocalTime localTime) {
        LocalDate today = LocalDate.now(clock);
        return today.plusDays(1).atTime(localTime).toInstant(BERLIN);
    }
}
