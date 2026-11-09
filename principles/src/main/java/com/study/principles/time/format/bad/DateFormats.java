package com.study.principles.time.format.bad;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Shared date formats of a UI/API test framework: query parameters for the reports API, the date
 * and time as the web UI shows them.
 *
 * <h2>What is wrong</h2>
 * <ul>
 *   <li><b>{@code YYYY} is the week-based year</b>, not the calendar year. For the last days of
 *       December (or the first days of January, depending on the locale's week rules) it prints the
 *       neighbouring year: {@code 2026-12-28} becomes {@code 2027-12-28}. Note it is a
 *       {@code java.time} formatter: the modern API does not protect you from the wrong letter.</li>
 *   <li><b>{@code hh} is the 12-hour clock</b> without an AM/PM marker: 13:05 prints as {@code 01:05}.
 *       Tests written in the morning pass.</li>
 *   <li><b>Shared static {@link SimpleDateFormat}.</b> It keeps parse state in fields and is not
 *       thread-safe: in parallel tests it occasionally returns a wrong date or throws.</li>
 *   <li><b>{@link SimpleDateFormat} is lenient</b>: {@code 30.02.2026} parses as 2 March, so a test
 *       that types an invalid date into the UI computes a "valid" expected value.</li>
 *   <li><b>Default locale</b>: month names come out in the runner's language
 *       ({@code November} on CI, {@code ноября} on a Russian laptop).</li>
 *   <li><b>{@code java.util.Date} round trip through the default zone</b>: {@code LocalDate} has no
 *       zone, {@code Date} is an instant; the conversion back depends on the runner's zone.</li>
 * </ul>
 */
public final class DateFormats {
    /** Reports API: {@code ?from=2026-11-09}. */
    public static final DateTimeFormatter QUERY_DATE = DateTimeFormatter.ofPattern("YYYY-MM-dd", Locale.US);
    /** UI time column, 24-hour clock: {@code 13:05}. */
    public static final DateTimeFormatter UI_TIME = DateTimeFormatter.ofPattern("hh:mm");
    /** UI date input: {@code 09.11.2026}. */
    public static final SimpleDateFormat UI_DATE = new SimpleDateFormat("dd.MM.yyyy");

    private DateFormats() {
    }

    public static String queryDate(LocalDate date) {
        return QUERY_DATE.format(date);
    }

    public static String uiTime(LocalTime time) {
        return UI_TIME.format(time);
    }

    public static LocalDate parseUiDate(String text) throws ParseException {
        return UI_DATE.parse(text).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }

    /** Page header: {@code 9 November 2026}. */
    public static String longDate(LocalDate date) {
        return DateTimeFormatter.ofPattern("d MMMM yyyy").format(date);
    }
}
