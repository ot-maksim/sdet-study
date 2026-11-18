package com.study.principles.inheritance.pages.good;

import com.study.principles.inheritance.pages.FakeBrowser;

/** The burger menu, written once — every page that has a header gets it on mobile. */
final class MobileHeader implements Header {

    private final FakeBrowser browser;

    MobileHeader(FakeBrowser browser) {
        this.browser = browser;
    }

    @Override
    public String userName() {
        browser.click("#burger");
        String name = browser.text("#user-name");
        browser.click("#burger");
        return name;
    }

    @Override
    public void logout() {
        browser.click("#burger");
        browser.click("#logout");
    }
}
