package com.study.principles.inheritance.session.good;

import com.study.principles.inheritance.session.BrowserFarm;
import com.study.principles.inheritance.session.FakeBrowser;
import com.study.principles.inheritance.session.FakePage;

/** The subclass only knows HOW to acquire/release; it cannot break the lifecycle. */
public class WebSession extends DriverSession {
    private final BrowserFarm farm;
    private FakeBrowser browser;
    private FakePage page;

    public WebSession(BrowserFarm farm) {
        this.farm = farm;
    }

    @Override
    protected void doStart() {
        browser = farm.launch("chromium");
        page = browser.newPage();
    }

    @Override
    protected void doRelease() {
        if (browser != null) browser.close();
    }

    public FakePage page() {
        ensureStarted();
        return page;
    }
}
