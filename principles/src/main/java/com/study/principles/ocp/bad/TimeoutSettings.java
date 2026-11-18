package com.study.principles.ocp.bad;

import com.study.principles.ocp.Timeouts;

import java.time.Duration;

/**
 * Switch #2. {@code default} is "a safe fallback" — so {@code remote}, which needs long timeouts (network hop
 * to the grid), silently gets local Chrome values and flakes. Nobody gets an error; the suite just gets red
 * sometimes.
 */
public final class TimeoutSettings {

    private TimeoutSettings() {
    }

    public static Timeouts forBrowser(String browser) {
        return switch (browser) {
            case "firefox" -> new Timeouts(Duration.ofSeconds(20), Duration.ofSeconds(7));
            case "webkit", "mobile-web" -> new Timeouts(Duration.ofSeconds(25), Duration.ofSeconds(8));
            default -> new Timeouts(Duration.ofSeconds(15), Duration.ofSeconds(5));   // chrome
        };
    }
}
