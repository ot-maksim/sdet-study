package com.study.principles.time.format;

import com.study.principles.time.format.good.DateFormats;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.text.ParseException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateFormatsTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "2026-12-25, 2026-12-25",
            "2026-12-27, 2027-12-27",   // Sunday: the first week of 2027 (US weeks) has started
            "2026-12-31, 2027-12-31",
    })
    void badWeekBasedYearJumpsAYearAtTheEndOfDecember(LocalDate date, String printed) {
        assertThat(com.study.principles.time.format.bad.DateFormats.queryDate(date)).isEqualTo(printed);
    }

    @Test
    void weekBasedYearDependsOnTheLocaleWeekRulesToo() {
        var withGermanWeeks = DateTimeFormatter.ofPattern("YYYY-MM-dd", Locale.GERMANY);

        assertThat(withGermanWeeks.format(LocalDate.of(2026, 12, 31))).isEqualTo("2026-12-31");
        assertThat(withGermanWeeks.format(LocalDate.of(2027, 1, 1))).isEqualTo("2026-01-01");
    }

    @Test
    void badTwelveHourClockLooksRightInTheMorning() {
        assertThat(com.study.principles.time.format.bad.DateFormats.uiTime(LocalTime.of(9, 5))).isEqualTo("09:05");
        assertThat(com.study.principles.time.format.bad.DateFormats.uiTime(LocalTime.of(13, 5))).isEqualTo("01:05");
    }

    @Test
    void badLenientParserTurnsAnInvalidDateIntoMarch() throws ParseException {
        assertThat(com.study.principles.time.format.bad.DateFormats.parseUiDate("30.02.2026")).isEqualTo("2026-03-02");
        assertThat(com.study.principles.time.format.bad.DateFormats.parseUiDate("09.13.2026")).isEqualTo("2027-01-09");
    }

    @Test
    void badLongDateSpeaksTheRunnersLanguage() {
        LocalDate date = LocalDate.of(2026, 11, 9);

        assertThat(withDefaultLocale(Locale.US, () -> com.study.principles.time.format.bad.DateFormats.longDate(date)))
                .isEqualTo("9 November 2026");
        assertThat(withDefaultLocale(Locale.forLanguageTag("ru"), () -> com.study.principles.time.format.bad.DateFormats.longDate(date)))
                .isEqualTo("9 ноября 2026");
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-12-27", "2026-12-31", "2027-01-01", "2028-02-29"})
    void goodQueryDateIsTheIsoDate(LocalDate date) {
        assertThat(DateFormats.queryDate(date)).isEqualTo(date.toString());
    }

    @Test
    void goodTimeIsTwentyFourHour() {
        assertThat(DateFormats.uiTime(LocalTime.of(13, 5))).isEqualTo("13:05");
    }

    @ParameterizedTest
    @ValueSource(strings = {"30.02.2026", "29.02.2027", "09.13.2026", "9.11.2026", "09.11.26"})
    void goodStrictParserRejectsWhatIsNotARealDate(String text) {
        assertThatThrownBy(() -> DateFormats.parseUiDate(text)).isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void goodStrictParserAcceptsALeapDay() {
        assertThat(DateFormats.parseUiDate("29.02.2028")).isEqualTo("2028-02-29");
    }

    @Test
    void goodLongDateUsesTheUiLocaleWhateverTheRunner() {
        LocalDate date = LocalDate.of(2026, 11, 9);

        String onRussianLaptop = withDefaultLocale(Locale.forLanguageTag("ru"), () -> DateFormats.longDate(date, Locale.US));

        assertThat(onRussianLaptop).isEqualTo("9 November 2026");
        assertThat(DateFormats.longDate(date, Locale.forLanguageTag("ru"))).isEqualTo("9 ноября 2026");
    }

    @Test
    void yyyyNeedsAnEraInStrictModeWhichIsWhyStrictPatternsUseUuuu() {
        var strictYearOfEra = DateTimeFormatter.ofPattern("dd.MM.yyyy")
                .withResolverStyle(java.time.format.ResolverStyle.STRICT);

        assertThatThrownBy(() -> LocalDate.parse("09.11.2026", strictYearOfEra)).isInstanceOf(DateTimeParseException.class);
    }

    private static <T> T withDefaultLocale(Locale locale, Supplier<T> action) {
        Locale original = Locale.getDefault();
        Locale.setDefault(locale);
        try {
            return action.get();
        } finally {
            Locale.setDefault(original);
        }
    }
}
