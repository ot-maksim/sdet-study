package com.study.principles.coupling.bad.newinside;

import com.study.principles.coupling.Browser;
import com.study.principles.coupling.FakeBrowser;

/**
 * SMELL: creation coupling. The page decides which browser, when it starts, and that it gets its own.
 * <ul>
 *   <li>every page is a separate browser — a separate user session;</li>
 *   <li>the type is fixed: swapping Chromium for WebKit, a remote grid or a fake means editing every page;</li>
 *   <li>nobody owns the browser, so nobody closes it — hence the {@code getBrowser()} "for teardown",
 *       which hands the internals to every test (content coupling).</li>
 * </ul>
 */
public class LoginPage {

    private final Browser browser = new FakeBrowser();   // in real life: Playwright.create().chromium().launch()...

    public void open() {
        browser.open("/login");
    }

    public void loginAs(String user, String password) {
        browser.fill("username", user);
        browser.fill("password", password);
        browser.click("sign-in");
    }

    public Browser getBrowser() {
        return browser;
    }
}
