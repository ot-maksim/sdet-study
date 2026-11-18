package com.study.principles.inheritance.pages.bad;

import com.study.principles.inheritance.pages.FakeBrowser;

public abstract class AuthenticatedPage extends BasePage {

    protected AuthenticatedPage(FakeBrowser browser) {
        super(browser);
    }

    public String userName() {
        return browser.text("#user-name");
    }

    public void logout() {
        browser.click("#logout");
    }
}
