package com.study.principles.time.types.good;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * The same test data with each concept in its own type.
 *
 * <h2>Design decisions</h2>
 * <ul>
 *   <li><b>{@link ZoneId} ({@code Europe/Berlin}), not an offset.</b> The zone knows the DST rules;
 *       the offset is derived for the specific date.</li>
 *   <li><b>"Today" is asked in the office zone</b> ({@code clock.withZone(officeZone)}): the calendar
 *       that matters is the office's, not the runner's or the clock's default.</li>
 *   <li><b>Calendar arithmetic on {@link LocalDate}</b> ({@code plusDays(1)}), then
 *       {@code atZone(..)} once, then {@link Instant} for the wire. Local types for human concepts,
 *       {@code Instant} for the moment the API stores.</li>
 *   <li><b>Gaps are resolved by {@code atZone}</b>: 02:30 on the spring-forward day does not exist
 *       in Berlin and becomes 03:30. If the test needs a different policy, it must be explicit
 *       ({@code ZonedDateTime.ofStrict} throws).</li>
 * </ul>
 */
public final class MeetingRequests {
    private final Clock clock;
    private final ZoneId officeZone;

    public MeetingRequests(Clock clock, ZoneId officeZone) {
        this.clock = clock;
        this.officeZone = officeZone;
    }

    public Instant tomorrowAt(LocalTime localTime) {
        LocalDate today = LocalDate.now(clock.withZone(officeZone));
        return today.plusDays(1).atTime(localTime).atZone(officeZone).toInstant();
    }
}
