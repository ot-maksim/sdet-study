package com.study.principles.inheritance.pages.bad;

import com.study.principles.inheritance.pages.FakeBrowser;

public abstract class PageWithSidebar extends AuthenticatedPage {

    protected PageWithSidebar(FakeBrowser browser) {
        super(browser);
    }

    public void navigateTo(String item) {
        browser.click("#nav-" + item);
    }
}
