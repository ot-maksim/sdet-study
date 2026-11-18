package com.study.principles.inheritance.pages.bad;

import com.study.principles.inheritance.pages.FakeBrowser;

/** Nobody wrote MobileReportsPage yet — on mobile, tests use this one and logout fails. */
public class ReportsPage extends PageWithSidebar {

    public ReportsPage(FakeBrowser browser) {
        super(browser);
    }

    @Override
    protected String path() {
        return "/reports";
    }
}
