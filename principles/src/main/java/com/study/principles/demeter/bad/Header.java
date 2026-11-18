package com.study.principles.demeter.bad;

import com.study.principles.demeter.FakeApp;

public class Header {

    private final FakeApp app;

    Header(FakeApp app) {
        this.app = app;
    }

    public UserMenu getUserMenu() {
        return new UserMenu(app, "header > user-menu");
    }
}
