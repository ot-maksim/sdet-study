package com.study.principles.inheritance.pages.good;

import com.study.principles.inheritance.pages.FakeBrowser;

public final class Sidebar {

    private final FakeBrowser browser;

    Sidebar(FakeBrowser browser) {
        this.browser = browser;
    }

    public void navigateTo(String item) {
        browser.click("#nav-" + item);
    }
}
