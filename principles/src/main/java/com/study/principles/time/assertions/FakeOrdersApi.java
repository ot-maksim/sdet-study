package com.study.principles.time.assertions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Fake "orders" service with the habits of a real one:
 * <ul>
 *   <li><b>its own clock and zone</b> (another machine: may be ahead of or behind the test runner,
 *       deployed in UTC on one stand and in {@code Europe/Berlin} on another); the zone is taken
 *       from {@code serverClock.getZone()};</li>
 *   <li>stores timestamps with <b>millisecond</b> precision (as JavaScript, MySQL {@code DATETIME(3)}
 *       or many JSON serializers do; Postgres keeps microseconds, Java's {@code Instant.now()} has
 *       microseconds on Linux/macOS since JDK 15);</li>
 *   <li>serializes {@code createdAt} in the server zone with a fixed pattern
 *       ({@code 2026-11-09T15:45:59.000+01:00}) and {@code expiresAt} as a UTC instant
 *       ({@code 2026-11-09T15:15:59Z}): two serializers in one payload, which happens.</li>
 * </ul>
 */
public final class FakeOrdersApi {
    public static final Duration ORDER_TTL = Duration.ofMinutes(30);
    private static final DateTimeFormatter CREATED_AT = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSXXX");

    private final Clock serverClock;
    private int sequence;

    public FakeOrdersApi(Clock serverClock) {
        this.serverClock = serverClock;
    }

    public synchronized OrderResponse createOrder() {
        Instant now = serverClock.instant().truncatedTo(ChronoUnit.MILLIS);
        sequence++;
        return new OrderResponse("order-" + sequence,
                CREATED_AT.withZone(serverClock.getZone()).format(now),
                DateTimeFormatter.ISO_INSTANT.format(now.plus(ORDER_TTL)));
    }
}
