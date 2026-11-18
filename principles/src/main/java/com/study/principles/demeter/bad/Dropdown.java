package com.study.principles.demeter.bad;

import com.study.principles.demeter.FakeApp;

public class Dropdown {

    private final FakeApp app;
    private final String menuSelector;

    Dropdown(FakeApp app, String menuSelector) {
        this.app = app;
        this.menuSelector = menuSelector;
    }

    public MenuItem getItem(String name) {
        return new MenuItem(app, menuSelector + " > item:" + name);
    }
}
