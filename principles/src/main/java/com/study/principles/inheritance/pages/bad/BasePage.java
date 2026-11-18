package com.study.principles.inheritance.pages.bad;

import com.study.principles.inheritance.pages.FakeBrowser;

/**
 * Root of a hierarchy that models the screen as a chain of "is-a":
 * <pre>
 *   BasePage
 *   └── AuthenticatedPage                    header (desktop selectors)
 *       └── PageWithSidebar                  + sidebar
 *           ├── ReportsPage
 *           └── PageWithSidebarAndSearch     + search
 *               ├── DashboardPage
 *               │   └── MobileDashboardPage  overrides the header
 *               └── SettingsPage             has no sidebar, but search lives only here
 *                   └── MobileSettingsPage   overrides the header — again
 * </pre>
 * Widgets are independent (any page has any subset), a class has one parent: the tree cannot fit.
 */
public abstract class BasePage {

    protected final FakeBrowser browser;

    protected BasePage(FakeBrowser browser) {
        this.browser = browser;
    }

    protected abstract String path();

    public void open() {
        browser.open(path());
    }
}
