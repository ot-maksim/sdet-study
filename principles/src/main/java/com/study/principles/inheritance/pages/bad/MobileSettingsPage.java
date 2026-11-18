package com.study.principles.inheritance.pages.bad;

import com.study.principles.inheritance.pages.FakeBrowser;

/** Copy of {@link MobileDashboardPage}'s overrides: they cannot be shared, the parents differ. */
public class MobileSettingsPage extends SettingsPage {

    public MobileSettingsPage(FakeBrowser browser) {
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
