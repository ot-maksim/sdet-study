package com.study.principles.time.assertions.bad;

import com.study.principles.time.assertions.OrderResponse;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Assertion helpers for order timestamps, as they often look in a framework.
 * (Assertion helpers are test code and AssertJ is test-scoped, so this kata keeps them under
 * {@code src/test}; the fake API they check lives in {@code main}.)
 *
 * <h2>What is wrong</h2>
 * <ul>
 *   <li>{@link #assertCreatedJustNow}: compares <b>strings</b> with the <b>runner's</b> local time.
 *       Wrong as soon as the server is in another zone or its clock is a second off, flaky at every
 *       second boundary.</li>
 *   <li>{@link #assertCreatedBetween}: the right idea (a time window), but it ignores the server's
 *       <b>precision</b>. {@code before} has microseconds, the server truncates to milliseconds, so
 *       a correct {@code createdAt} can be <i>earlier</i> than {@code before}.</li>
 *   <li>{@link #assertTimes}: an expected DTO with {@link OffsetDateTime} fields. AssertJ compares a
 *       top-level {@code OffsetDateTime} by instant, but a record compares its fields with
 *       {@code equals}, which includes the <b>offset</b>. Passes while the server runs in UTC,
 *       fails on the stand deployed in Berlin.</li>
 * </ul>
 */
public final class OrderAssertions {

    private OrderAssertions() {
    }

    public static void assertCreatedJustNow(OrderResponse order) {
        String now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString();
        assertThat(order.createdAt()).startsWith(now);
    }

    public static void assertCreatedBetween(OrderResponse order, Instant before, Instant after) {
        Instant createdAt = OffsetDateTime.parse(order.createdAt()).toInstant();
        assertThat(createdAt).isBetween(before, after);
    }

    public record OrderTimes(OffsetDateTime createdAt, OffsetDateTime expiresAt) {
    }

    public static void assertTimes(OrderResponse order, OrderTimes expected) {
        var actual = new OrderTimes(OffsetDateTime.parse(order.createdAt()), OffsetDateTime.parse(order.expiresAt()));
        assertThat(actual).isEqualTo(expected);
    }
}
