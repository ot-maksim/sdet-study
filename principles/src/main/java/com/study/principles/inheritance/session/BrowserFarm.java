package com.study.principles.inheritance.session;

import java.util.ArrayList;
import java.util.List;

/**
 * Fake stand-in for Playwright/Appium: launches "browsers" and remembers which ones are still open.
 * Lets the katas show resource leaks without downloading real browsers.
 *
 * Knobs:
 *  - {@link #failOnNewPage()}  — simulates a start that fails halfway (browser launched, page not created)
 */
public class BrowserFarm {
    private final List<FakeBrowser> launched = new ArrayList<>();
    private boolean failOnNewPage;

    public FakeBrowser launch(String name) {
        FakeBrowser browser = new FakeBrowser(name, this);
        launched.add(browser);
        return browser;
    }

    public BrowserFarm failOnNewPage() {
        this.failOnNewPage = true;
        return this;
    }

    boolean shouldFailOnNewPage() {
        return failOnNewPage;
    }

    /** How many browsers are still open — a leak if > 0 after all sessions were closed. */
    public long openBrowsers() {
        return launched.stream().filter(FakeBrowser::isOpen).count();
    }
}
