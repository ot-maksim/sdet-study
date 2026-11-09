package com.study.principles.time;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test clock that stands still until the test moves it. {@link Clock#fixed} is enough when the code
 * reads the time once; when the test has to <i>let time pass</i> (a token expires, a trial ends),
 * use this one and call {@link #advance(Duration)} instead of {@code Thread.sleep}.
 *
 * <h2>Design decisions</h2>
 * <ul>
 *   <li><b>{@link #withZone} shares the time.</b> Code under test often calls
 *       {@code clock.withZone(officeZone)}. If that returned an independent copy, advancing the
 *       original would not move the copy and the test would silently test nothing. Both views read
 *       the same {@link AtomicReference}.</li>
 *   <li><b>Thread-safe</b> ({@link AtomicReference}), so it can be shared by parallel steps.</li>
 *   <li><b>Lives in {@code main}</b> only because katas keep their fakes there; in a real framework
 *       it belongs to the test-support module, never to production code.</li>
 * </ul>
 */
public final class MutableClock extends Clock {
    private final AtomicReference<Instant> now;
    private final ZoneId zone;

    private MutableClock(AtomicReference<Instant> now, ZoneId zone) {
        this.now = now;
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    /** A clock standing at {@code start}; {@code zone} is what {@code LocalDate.now(clock)} uses. */
    public MutableClock(Instant start, ZoneId zone) {
        this(new AtomicReference<>(Objects.requireNonNull(start, "start")), zone);
    }

    /** Moves time forward (or backward, for a negative duration, e.g. to simulate NTP correction). */
    public MutableClock advance(Duration duration) {
        now.updateAndGet(current -> current.plus(duration));
        return this;
    }

    /** Jumps to an exact moment. */
    public MutableClock set(Instant instant) {
        now.set(Objects.requireNonNull(instant, "instant"));
        return this;
    }

    @Override
    public Instant instant() {
        return now.get();
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return zone.equals(this.zone) ? this : new MutableClock(now, zone);
    }

    @Override
    public String toString() {
        return "MutableClock[" + instant() + ", " + zone + "]";
    }
}
