package com.study.principles.time.assertions;

import com.study.principles.time.MutableClock;
import com.study.principles.time.assertions.bad.OrderAssertions.OrderTimes;
import com.study.principles.time.assertions.good.OrderAssertions;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Every response in this class is correct. The bad helpers reject some of them; the good ones
 * accept all of them and still catch a real defect.
 */
class OrderAssertionsTest {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    /** A runner timestamp with microseconds, as {@code Instant.now()} gives on Linux/macOS. */
    private static final Instant MOMENT = Instant.parse("2026-11-09T14:45:59.441646Z");

    @Test
    void badJustNowFailsWhenTheServerClockIsTwoSecondsAhead() {
        var api = new FakeOrdersApi(Clock.offset(Clock.system(ZoneId.systemDefault()), Duration.ofSeconds(2)));

        OrderResponse order = api.createOrder();

        assertThatThrownBy(() -> com.study.principles.time.assertions.bad.OrderAssertions.assertCreatedJustNow(order))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void goodNearAcceptsClockSkewWithinTheTolerance() {
        var api = new FakeOrdersApi(Clock.offset(Clock.system(BERLIN), Duration.ofSeconds(2)));

        OrderResponse order = api.createOrder();

        OrderAssertions.assertCreatedNear(order, Instant.now(), Duration.ofSeconds(5));
    }

    @Test
    void goodNearStillCatchesATimestampThatIsReallyWrong() {
        var api = new FakeOrdersApi(Clock.offset(Clock.system(BERLIN), Duration.ofHours(-1)));   // "forgot the zone" bug

        OrderResponse order = api.createOrder();

        assertThatThrownBy(() -> OrderAssertions.assertCreatedNear(order, Instant.now(), Duration.ofSeconds(5)))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void badWindowRejectsACorrectTimestampTruncatedToMillis() {
        var clock = new MutableClock(MOMENT, BERLIN);   // runner and server share the clock (in-process SUT)
        var api = new FakeOrdersApi(clock);

        Instant before = clock.instant();
        OrderResponse order = api.createOrder();       // stored as ...59.441, before is ...59.441646
        Instant after = clock.instant();

        assertThatThrownBy(() -> com.study.principles.time.assertions.bad.OrderAssertions.assertCreatedBetween(order, before, after))
                .isInstanceOf(AssertionError.class);
    }

    @Test
    void goodWindowTruncatesToTheServerPrecision() {
        var clock = new MutableClock(MOMENT, BERLIN);
        var api = new FakeOrdersApi(clock);

        Instant before = clock.instant();
        OrderResponse order = api.createOrder();
        Instant after = clock.instant();

        Instant createdAt = OrderAssertions.assertCreatedBetween(order, before, after, ChronoUnit.MILLIS);
        assertThat(createdAt).isEqualTo("2026-11-09T14:45:59.441Z");
    }

    @Test
    void goodWindowWithTheRealClock() {
        var api = new FakeOrdersApi(Clock.system(BERLIN));

        Instant before = Instant.now();
        OrderResponse order = api.createOrder();
        Instant after = Instant.now();

        OrderAssertions.assertCreatedBetween(order, before, after, ChronoUnit.MILLIS);
    }

    @Test
    void badExpectedDtoPassesOnTheUtcStandAndFailsOnTheBerlinStand() {
        OrderResponse onUtcStand = new FakeOrdersApi(Clock.fixed(MOMENT, ZoneOffset.UTC)).createOrder();
        OrderResponse onBerlinStand = new FakeOrdersApi(Clock.fixed(MOMENT, BERLIN)).createOrder();

        assertThatCode(() -> com.study.principles.time.assertions.bad.OrderAssertions.assertTimes(onUtcStand, expectedTimes(onUtcStand)))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> com.study.principles.time.assertions.bad.OrderAssertions.assertTimes(onBerlinStand, expectedTimes(onBerlinStand)))
                .isInstanceOf(AssertionError.class);
    }

    /** The trap behind the bad DTO: AssertJ compares a single OffsetDateTime by instant, equals() does not. */
    @Test
    void sameInstantDifferentOffsetEqualForAssertJButNotForEquals() {
        var berlin = OffsetDateTime.parse("2026-11-09T16:15:59.441+01:00");
        var utc = OffsetDateTime.parse("2026-11-09T15:15:59.441Z");

        assertThat(utc).isEqualTo(berlin);
        assertThat(utc).isAtSameInstantAs(berlin);
        assertThat(utc.equals(berlin)).isFalse();
        assertThat(new OrderTimes(utc, utc)).isNotEqualTo(new OrderTimes(berlin, berlin));
    }

    @Test
    void goodExpiryIsCheckedAsADurationOnBothStands() {
        for (ZoneId stand : new ZoneId[]{ZoneOffset.UTC, BERLIN}) {
            OrderResponse order = new FakeOrdersApi(Clock.fixed(MOMENT, stand)).createOrder();

            OrderAssertions.assertExpiresAfter(order, FakeOrdersApi.ORDER_TTL);
        }
    }

    /** What a test author writes: "expires 30 minutes after it was created". */
    private static OrderTimes expectedTimes(OrderResponse order) {
        OffsetDateTime createdAt = OffsetDateTime.parse(order.createdAt());
        return new OrderTimes(createdAt, createdAt.plus(FakeOrdersApi.ORDER_TTL));
    }
}
