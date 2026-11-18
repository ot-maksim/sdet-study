package com.study.principles.demeter.bad;

import com.study.principles.demeter.FakeApp;

public class MenuItem {

    private final FakeApp app;
    private final String selector;

    MenuItem(FakeApp app, String selector) {
        this.app = app;
        this.selector = selector;
    }

    public void click() {
        app.click(selector);
    }
}
