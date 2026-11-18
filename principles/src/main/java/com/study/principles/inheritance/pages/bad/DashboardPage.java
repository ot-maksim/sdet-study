package com.study.principles.inheritance.pages.bad;

import com.study.principles.inheritance.pages.FakeBrowser;

public class DashboardPage extends PageWithSidebarAndSearch {

    public DashboardPage(FakeBrowser browser) {
        super(browser);
    }

    @Override
    protected String path() {
        return "/dashboard";
    }
}
