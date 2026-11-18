package com.study.principles.coupling.good;

import com.study.principles.coupling.Browser;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Factory injection, case 2: the test itself decides <i>how many</i> browsers it needs (admin and user
 * side by side, a second tab, a fresh anonymous session). It gets a factory, not a browser.
 * <p>
 * The factory is also the owner: everything it launched is closed with it. The test never closes
 * browsers by hand and cannot forget one.
 */
public final class Browsers implements AutoCloseable {

    private final Supplier<Browser> launcher;
    private final List<Browser> launched = new ArrayList<>();

    public Browsers(Supplier<Browser> launcher) {
        this.launcher = launcher;
    }

    public Pages newSession() {
        return new Pages(launch());
    }

    public synchronized Browser launch() {
        Browser browser = launcher.get();
        launched.add(browser);
        return browser;
    }

    public synchronized List<Browser> launched() {
        return List.copyOf(launched);
    }

    @Override
    public synchronized void close() {
        launched.forEach(Browser::close);
    }
}
