package com.study.principles.inheritance.pages.bad;

import com.study.principles.inheritance.pages.FakeBrowser;

/** Mobile = a second axis of variation. Inheritance can only add it per leaf: one override per page. */
public class MobileDashboardPage extends DashboardPage {

    public MobileDashboardPage(FakeBrowser browser) {
        super(browser);
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
