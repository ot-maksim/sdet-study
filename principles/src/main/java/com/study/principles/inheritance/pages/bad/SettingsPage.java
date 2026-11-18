package com.study.principles.inheritance.pages.bad;

import com.study.principles.inheritance.pages.FakeBrowser;

/**
 * Settings has a search box but no sidebar. The only parent with {@code search()} also brings
 * {@code navigateTo()}: <b>refused bequest</b>. It compiles, autocompletes, and fails at run time —
 * the subclass cannot honour its parent's contract (an LSP violation, see the LSP kata).
 */
public class SettingsPage extends PageWithSidebarAndSearch {

    public SettingsPage(FakeBrowser browser) {
        super(browser);
    }

    @Override
    protected String path() {
        return "/settings";
    }
}
