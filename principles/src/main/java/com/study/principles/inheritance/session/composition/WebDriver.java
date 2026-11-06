package com.study.principles.inheritance.session.composition;

import com.study.principles.inheritance.session.BrowserFarm;
import com.study.principles.inheritance.session.FakeBrowser;
import com.study.principles.inheritance.session.FakePage;

public class WebDriver implements SessionDriver {
    private final BrowserFarm farm;
    private FakeBrowser browser;

    public WebDriver(BrowserFarm farm) {
        this.farm = farm;
    }

    @Override
    public FakePage acquire() {
        browser = farm.launch("chromium");
        return browser.newPage();
    }

    @Override
    public void release() {
        if (browser != null) browser.close();
    }
}
