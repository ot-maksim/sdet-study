package com.study.principles.coupling.good;

import com.study.principles.coupling.Browser;

import java.util.Objects;

/**
 * Constructor injection: the signature is the full list of what the page needs. The page uses the
 * browser; it does not choose, start, share or close it — whoever created the browser owns it.
 */
public final class LoginPage {

    private final Browser browser;

    public LoginPage(Browser browser) {
        this.browser = Objects.requireNonNull(browser, "browser");
    }

    public LoginPage open() {
        browser.open("/login");
        return this;
    }

    /** Navigation returns the next page, built over the same browser — the same user session. */
    public DashboardPage loginAs(String user, String password) {
        browser.fill("username", user);
        browser.fill("password", password);
        browser.click("sign-in");
        return new DashboardPage(browser);
    }

    public String error() {
        return browser.text("error");
    }
}
