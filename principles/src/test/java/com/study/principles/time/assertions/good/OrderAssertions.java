package com.study.principles.time.assertions.good;

import com.study.principles.time.assertions.OrderResponse;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The same checks, each one asserting a property of points on the timeline.
 *
 * <h2>Design decisions</h2>
 * <ul>
 *   <li><b>Parse at the boundary, compare {@link Instant}s.</b> The wire format ({@code Z} vs
 *       {@code +01:00}, {@code .000} or not) is the serializer's business; the test asserts moments.</li>
 *   <li><b>Pick the check by who owns the clock.</b> Same clock (an in-process SUT, a fake, the DB of
 *       a test that reads it back): an exact window {@code [before, after]}, with {@code before}
 *       truncated to the server's precision. Remote server with its own clock: a tolerance around
 *       the runner's time ({@code isCloseTo}), sized for clock skew plus request latency.</li>
 *   <li><b>Relations between server timestamps are exact.</b> Both come from the same clock, so
 *       "expires 30 minutes after creation" needs no tolerance at all: {@code Duration.between}.</li>
 *   <li><b>Return the parsed value</b> so the test can keep asserting without parsing again.</li>
 * </ul>
 */
public final class OrderAssertions {

    private OrderAssertions() {
    }

    /** Same clock: {@code createdAt} lies within the window the test measured around the call. */
    public static Instant assertCreatedBetween(OrderResponse order, Instant before, Instant after,
                                               TemporalUnit serverPrecision) {
        Instant createdAt = instant(order.createdAt());
        assertThat(createdAt).isBetween(before.truncatedTo(serverPrecision), after);
        return createdAt;
    }

    /** Server with its own clock: {@code createdAt} is near the runner's time, skew included. */
    public static Instant assertCreatedNear(OrderResponse order, Instant reference, Duration tolerance) {
        Instant createdAt = instant(order.createdAt());
        assertThat(createdAt).isCloseTo(reference, within(tolerance.toMillis(), ChronoUnit.MILLIS));
        return createdAt;
    }

    /** Both timestamps come from the server's clock, so the relation between them is exact. */
    public static void assertExpiresAfter(OrderResponse order, Duration ttl) {
        assertThat(Duration.between(instant(order.createdAt()), instant(order.expiresAt()))).isEqualTo(ttl);
    }

    private static Instant instant(String isoOffsetDateTime) {
        return OffsetDateTime.parse(isoOffsetDateTime).toInstant();
    }
}
