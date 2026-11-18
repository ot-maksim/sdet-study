package com.study.principles.inheritance.pages.good;

import com.study.principles.inheritance.pages.FakeBrowser;

public final class SearchBox {

    private final FakeBrowser browser;

    SearchBox(FakeBrowser browser) {
        this.browser = browser;
    }

    public String search(String query) {
        browser.type("#search", query);
        browser.click("#search-go");
        return browser.text("#results");
    }
}
