package com.study.principles.coupling.bad.captive;

import com.study.principles.coupling.Browser;

/**
 * SMELL: captive dependency. Constructor injection is here — and still wrong, because of the lifetime:
 * a lazy singleton ("steps are stateless, why create them per test?") captures the browser of the
 * <i>first</i> test and keeps it after that test closed it. Every later test gets steps bound to a dead
 * browser, whatever browser it passes in.
 * <p>
 * Rule: an object may hold only dependencies that live at least as long as it does.
 * Singleton → per-test is the inversion that bites.
 */
public final class LoginSteps {

    private static LoginSteps instance;

    private final Browser browser;

    private LoginSteps(Browser browser) {
        this.browser = browser;
    }

    public static synchronized LoginSteps instance(Browser browser) {
        if (instance == null) instance = new LoginSteps(browser);   // the argument of later calls is ignored
        return instance;
    }

    public String loginAs(String user, String password) {
        browser.open("/login");
        browser.fill("username", user);
        browser.fill("password", password);
        browser.click("sign-in");
        return browser.text("greeting");
    }

    /** Test hook — the fact that one is needed is itself the smell. */
    public static synchronized void reset() {
        instance = null;
    }
}
