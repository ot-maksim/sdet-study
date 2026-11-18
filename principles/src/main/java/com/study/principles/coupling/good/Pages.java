package com.study.principles.coupling.good;

import com.study.principles.coupling.Browser;

/**
 * Factory injection, case 1: a test needs many page objects, all over one browser. Inject the factory
 * bound to that browser instead of the browser plus {@code new} in every test — and instead of the whole
 * container ("give me anything"), which would be a service locator again.
 */
public final class Pages {

    private final Browser browser;

    public Pages(Browser browser) {
        this.browser = browser;
    }

    public LoginPage login() {
        return new LoginPage(browser);
    }

    public DashboardPage dashboard() {
        return new DashboardPage(browser);
    }
}
