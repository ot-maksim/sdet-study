package com.study.principles.inheritance.pages.bad;

import com.study.principles.inheritance.pages.FakeBrowser;

/** Name lists the widgets — a sure sign the hierarchy enumerates combinations. Next: PageWithSearch? */
public abstract class PageWithSidebarAndSearch extends PageWithSidebar {

    protected PageWithSidebarAndSearch(FakeBrowser browser) {
        super(browser);
    }

    public String search(String query) {
        browser.type("#search", query);
        browser.click("#search-go");
        return browser.text("#results");
    }
}
