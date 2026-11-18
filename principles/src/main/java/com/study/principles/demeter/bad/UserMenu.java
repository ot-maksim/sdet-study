package com.study.principles.demeter.bad;

import com.study.principles.demeter.FakeApp;

/** "Ask" style: hands out the next object instead of doing the job. The caller must know to {@code open()} first. */
public class UserMenu {

    private final FakeApp app;
    private final String selector;

    UserMenu(FakeApp app, String selector) {
        this.app = app;
        this.selector = selector;
    }

    public UserMenu open() {
        app.click(selector);
        return this;
    }

    public Dropdown getDropdown() {
        return new Dropdown(app, selector);
    }
}
