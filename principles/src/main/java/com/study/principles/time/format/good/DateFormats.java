package com.study.principles.time.format.good;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Locale;

/**
 * The same formats, written so that the pattern means exactly one thing on every machine.
 *
 * <h2>Design decisions</h2>
 * <ul>
 *   <li><b>Built-in ISO formatters for machine formats</b> ({@link DateTimeFormatter#ISO_LOCAL_DATE}).
 *       No pattern letters, no chance to type {@code YYYY}.</li>
 *   <li><b>{@code uuuu} + {@link ResolverStyle#STRICT}</b> for the human format: {@code 30.02.2026}
 *       is rejected. ({@code yyyy} is year-of-era and needs an era field in STRICT mode, which is
 *       why STRICT patterns use {@code uuuu}.)</li>
 *   <li><b>{@code HH}</b> for the 24-hour clock.</li>
 *   <li><b>Explicit locale</b>: {@link Locale#ROOT} for numeric formats, and the UI locale taken from
 *       the test's configuration for month names, never {@code Locale.getDefault()}.</li>
 *   <li><b>{@link DateTimeFormatter} is immutable and thread-safe</b>, so shared constants are fine.</li>
 *   <li><b>{@code LocalDate} in, {@code LocalDate} out</b>: no detour through {@code java.util.Date}
 *       and the runner's zone.</li>
 * </ul>
 */
public final class DateFormats {
    public static final DateTimeFormatter QUERY_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    public static final DateTimeFormatter UI_TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    public static final DateTimeFormatter UI_DATE =
            DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    private DateFormats() {
    }

    public static String queryDate(LocalDate date) {
        return QUERY_DATE.format(date);
    }

    public static String uiTime(LocalTime time) {
        return UI_TIME.format(time);
    }

    /** @throws DateTimeParseException for text that is not a real calendar date, e.g. {@code 30.02.2026} */
    public static LocalDate parseUiDate(String text) {
        return LocalDate.parse(text, UI_DATE);
    }

    /** Page header in the UI's language: {@code 9 November 2026} / {@code 9 ноября 2026}. */
    public static String longDate(LocalDate date, Locale uiLocale) {
        return DateTimeFormatter.ofPattern("d MMMM uuuu", uiLocale).format(date);
    }
}
