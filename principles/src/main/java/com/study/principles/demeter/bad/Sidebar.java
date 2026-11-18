package com.study.principles.demeter.bad;

import com.study.principles.demeter.FakeApp;

/** Added for the V2 redesign. Every test that walked through {@link Header} has to be re-routed here. */
public class Sidebar {

    private final FakeApp app;

    Sidebar(FakeApp app) {
        this.app = app;
    }

    public UserMenu getUserMenu() {
        return new UserMenu(app, "sidebar > user-menu");
    }
}
