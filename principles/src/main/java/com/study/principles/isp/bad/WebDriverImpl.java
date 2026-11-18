package com.study.principles.isp.bad;

import com.study.principles.isp.FakeScreen;

import java.util.HashMap;
import java.util.Map;

/** Playwright-backed. Four of the 13 methods cannot be honoured — stubs that throw (an LSP violation, forced by the interface). */
public final class WebDriverImpl implements Driver {

    private final FakeScreen screen;
    private final Map<String, String> cookies = new HashMap<>();
    private String url = "about:blank";

    public WebDriverImpl(FakeScreen screen) {
        this.screen = screen;
    }

    @Override
    public void open(String url) {
        this.url = url;
        String session = cookies.get("session");
        if (session != null) screen.restoreSession(session);
    }

    @Override
    public String currentUrl() {
        return url;
    }

    @Override
    public void click(String id) {
        screen.press(id);
    }

    @Override
    public void type(String id, String text) {
        screen.fill(id, text);
    }

    @Override
    public String text(String id) {
        return screen.read(id);
    }

    @Override
    public String screenshot(String name) {
        return screen.screenshot(name);
    }

    @Override
    public Object executeScript(String script) {
        return "evaluated: " + script;
    }

    @Override
    public void addCookie(String name, String value) {
        cookies.put(name, value);
    }

    @Override
    public void clearCookies() {
        cookies.clear();
    }

    @Override
    public void tap(String id) {
        throw new UnsupportedOperationException("tap is not supported on web");
    }

    @Override
    public void swipe(String direction) {
        throw new UnsupportedOperationException("swipe is not supported on web");
    }

    @Override
    public void installApp(String path) {
        throw new UnsupportedOperationException("installApp is not supported on web");
    }

    @Override
    public void launchApp(String bundleId) {
        throw new UnsupportedOperationException("launchApp is not supported on web");
    }
}
