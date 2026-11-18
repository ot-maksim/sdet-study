package com.study.principles.inheritance.pages.good;

import com.study.principles.inheritance.pages.Device;
import com.study.principles.inheritance.pages.FakeBrowser;

/**
 * Page factory bound to one browser. The device decision is made here, once: which {@link Header}
 * implementation every page gets. Adding a tablet profile = one more {@code Header} + one line here.
 */
public final class Pages {

    private final FakeBrowser browser;
    private final Header header;

    public Pages(FakeBrowser browser) {
        this.browser = browser;
        this.header = browser.device() == Device.MOBILE ? new MobileHeader(browser) : new DesktopHeader(browser);
    }

    public DashboardPage dashboard() {
        browser.open("/dashboard");
        return new DashboardPage(header, new Sidebar(browser), new SearchBox(browser));
    }

    public ReportsPage reports() {
        browser.open("/reports");
        return new ReportsPage(header, new Sidebar(browser));
    }

    public SettingsPage settings() {
        browser.open("/settings");
        return new SettingsPage(header, new SearchBox(browser));
    }
}
