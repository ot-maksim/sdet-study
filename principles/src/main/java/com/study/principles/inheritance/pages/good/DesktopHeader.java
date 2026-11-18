package com.study.principles.inheritance.pages.good;

import com.study.principles.inheritance.pages.FakeBrowser;

final class DesktopHeader implements Header {

    private final FakeBrowser browser;

    DesktopHeader(FakeBrowser browser) {
        this.browser = browser;
    }

    @Override
    public String userName() {
        return browser.text("#user-name");
    }

    @Override
    public void logout() {
        browser.click("#logout");
    }
}
