package com.study.principles.coupling.good;

import com.study.principles.coupling.Browser;

import java.util.Objects;

public final class DashboardPage {

    private final Browser browser;

    public DashboardPage(Browser browser) {
        this.browser = Objects.requireNonNull(browser, "browser");
    }

    public DashboardPage open() {
        browser.open("/dashboard");
        return this;
    }

    public String greeting() {
        return browser.text("greeting");
    }
}
