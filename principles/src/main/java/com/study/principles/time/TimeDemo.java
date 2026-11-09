package com.study.principles.time;

import com.study.principles.time.assertions.FakeOrdersApi;
import com.study.principles.time.clock.FakeTokenIssuer;
import com.study.principles.time.clock.good.AuthTokenCache;
import com.study.principles.time.format.bad.DateFormats;
import com.study.principles.time.types.bad.MeetingRequests;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runs the four parts of the kata side by side: injected clock, time types across DST,
 * formatting traps, and server timestamps as a test receives them.
 */
public class TimeDemo {
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    public static void main(String[] args) throws Exception {
        clock();
        types();
        format();
        serverTimestamps();
    }

    private static void clock() {
        System.out.println("=== CLOCK: a 15-minute token, tested without waiting ===");
        var clock = new MutableClock(Instant.parse("2026-11-09T10:00:00Z"), ZoneOffset.UTC);
        var issuer = new FakeTokenIssuer(clock, Duration.ofMinutes(15));
        var cache = new AuthTokenCache(issuer, clock);
        System.out.println("  10:00:00      -> " + cache.bearer());
        clock.advance(Duration.ofMinutes(14).plusSeconds(29));
        System.out.println("  +14:29        -> " + cache.bearer() + "   (31 s left, reused)");
        clock.advance(Duration.ofSeconds(1));
        System.out.println("  +14:30        -> " + cache.bearer() + "   (30 s left, refreshed)");
        System.out.println("  issued: " + issuer.issued() + ", real time spent: ~0 ms");
    }

    private static void types() {
        System.out.println("\n=== TYPES: \"tomorrow 09:00 in Berlin\" sent as an Instant ===");
        for (String now : new String[]{"2026-11-09T12:00:00Z", "2026-03-28T12:00:00Z", "2026-11-09T23:30:00Z"}) {
            Clock clock = Clock.fixed(Instant.parse(now), ZoneOffset.UTC);
            Instant bad = new MeetingRequests(clock).tomorrowAt(LocalTime.of(9, 0));
            Instant good = new com.study.principles.time.types.good.MeetingRequests(clock, BERLIN)
                    .tomorrowAt(LocalTime.of(9, 0));
            System.out.printf("  now %s  bad %s (%s Berlin)   good %s (%s Berlin)%n",
                    now, bad, bad.atZone(BERLIN).toLocalDateTime(), good, good.atZone(BERLIN).toLocalDateTime());
        }
    }

    private static void format() throws Exception {
        System.out.println("\n=== FORMAT ===");
        LocalDate dec28 = LocalDate.of(2026, 12, 28);
        System.out.println("  YYYY-MM-dd   " + dec28 + " -> " + DateFormats.queryDate(dec28)
                + "   good: " + com.study.principles.time.format.good.DateFormats.queryDate(dec28));
        System.out.println("  hh:mm        13:05 -> " + DateFormats.uiTime(LocalTime.of(13, 5))
                + "   good: " + com.study.principles.time.format.good.DateFormats.uiTime(LocalTime.of(13, 5)));
        System.out.println("  lenient      30.02.2026 -> " + DateFormats.parseUiDate("30.02.2026")
                + "   good: " + describe(() -> com.study.principles.time.format.good.DateFormats.parseUiDate("30.02.2026")));
        System.out.println("  locale       " + DateFormats.longDate(dec28) + "   (default locale: "
                + java.util.Locale.getDefault() + ")");
        System.out.println("  shared SimpleDateFormat, 8 threads: " + sharedSimpleDateFormatRace() + " wrong results");
    }

    /** Not a unit test on purpose: the race is real but not deterministic. */
    private static int sharedSimpleDateFormatRace() throws InterruptedException {
        var shared = new SimpleDateFormat("dd.MM.yyyy");
        var wrong = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (int t = 0; t < 8; t++) {
            String text = String.format("%02d.%02d.20%02d", t + 1, t + 1, 20 + t);
            pool.execute(() -> {
                for (int i = 0; i < 5_000; i++) {
                    try {
                        Date parsed = shared.parse(text);
                        if (!new SimpleDateFormat("dd.MM.yyyy").format(parsed).equals(text)) {
                            wrong.incrementAndGet();
                        }
                    } catch (ParseException | RuntimeException e) {
                        wrong.incrementAndGet();
                    }
                }
            });
        }
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
        return wrong.get();
    }

    private static void serverTimestamps() {
        System.out.println("\n=== ASSERTIONS: the same moment, as two stands serialize it ===");
        Instant moment = Instant.parse("2026-11-09T14:45:59.441646Z");
        System.out.println("  runner Instant.now(): " + moment);
        System.out.println("  stand in UTC:         " + new FakeOrdersApi(Clock.fixed(moment, ZoneOffset.UTC)).createOrder());
        System.out.println("  stand in Berlin:      " + new FakeOrdersApi(Clock.fixed(moment, BERLIN)).createOrder());
    }

    private static String describe(ThrowingSupplier<?> action) {
        try {
            return String.valueOf(action.get());
        } catch (Exception e) {
            return e.getClass().getSimpleName();
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
