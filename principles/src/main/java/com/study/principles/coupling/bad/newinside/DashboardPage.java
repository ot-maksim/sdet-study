package com.study.principles.coupling.bad.newinside;

import com.study.principles.coupling.Browser;
import com.study.principles.coupling.FakeBrowser;

/** Same smell as {@link LoginPage}: a second {@code new}, a second browser, a second (anonymous) user. */
public class DashboardPage {

    private final Browser browser = new FakeBrowser();

    public void open() {
        browser.open("/dashboard");
    }

    public String greeting() {
        return browser.text("greeting");
    }

    public Browser getBrowser() {
        return browser;
    }
}
