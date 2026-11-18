package com.study.principles.ocp.bad;

import com.study.principles.ocp.Browser;
import com.study.principles.ocp.Viewport;

/**
 * Switch #1 of 3 on the browser name. The state of the code right after "add Selenium-Grid-style remote
 * runs" was merged: the author found this switch, and missed {@link TimeoutSettings}; {@link ViewportSettings}
 * was never reached because the remote case goes through here only.
 * <p>
 * Every new browser = edit this class, {@link TimeoutSettings} and {@link ViewportSettings} — and nothing tells
 * you there are three.
 */
public final class DriverFactory {

    private DriverFactory() {
    }

    public static Browser create(String browser) {
        return switch (browser) {
            case "chrome" -> new Browser("chromium", "local", ViewportSettings.forBrowser("chrome"));
            case "firefox" -> new Browser("firefox", "local", ViewportSettings.forBrowser("firefox"));
            case "webkit" -> new Browser("webkit", "local", ViewportSettings.forBrowser("webkit"));
            case "mobile-web" -> new Browser("webkit", "local", ViewportSettings.forBrowser("mobile-web"));
            case "remote" -> new Browser("chromium", "http://grid:4444", Viewport.DESKTOP);   // added last sprint
            default -> throw new IllegalArgumentException("unknown browser: " + browser);
        };
    }
}
