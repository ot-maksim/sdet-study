package com.study.principles.demeter.good;

import com.study.principles.demeter.FakeApp;

/**
 * A component still exists — composition is fine — but it is package-private and also "tell" style:
 * {@code select(item)} owns the "open, then click the item" order instead of handing out a dropdown.
 */
final class UserMenu {

    private final FakeApp app;
    private final String selector;

    UserMenu(FakeApp app, String selector) {
        this.app = app;
        this.selector = selector;
    }

    void select(String item) {
        app.click(selector);
        app.click(selector + " > item:" + item);
    }
}
