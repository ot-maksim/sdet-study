package com.study.principles.demeter.bad;

import com.study.principles.demeter.FakeApp;

/**
 * SMELL: a page object that is a map of its components. It offers no actions — only getters — so every
 * test walks the structure itself: page → header → user menu → dropdown → item. The page's layout is
 * now part of every test's code.
 */
public class AppPage {

    private final FakeApp app;

    public AppPage(FakeApp app) {
        this.app = app;
    }

    public Header getHeader() {
        return new Header(app);
    }

    public Sidebar getSidebar() {
        return new Sidebar(app);
    }
}
