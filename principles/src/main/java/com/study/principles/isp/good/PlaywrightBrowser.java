package com.study.principles.isp.good;

import com.study.principles.isp.FakeScreen;

import java.util.HashMap;
import java.util.Map;

/**
 * Implements only what a browser can do — no stubs. {@code executeScript} is a public method without a role:
 * no client needed it yet, so no interface was invented for it (add one when the first client appears).
 */
public final class PlaywrightBrowser implements Browser {

    private final FakeScreen screen;
    private final Map<String, String> cookies = new HashMap<>();
    private String url = "about:blank";

    public PlaywrightBrowser(FakeScreen screen) {
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
    public void addCookie(String name, String value) {
        cookies.put(name, value);
    }

    @Override
    public void clearCookies() {
        cookies.clear();
    }

    public Object executeScript(String script) {
        return "evaluated: " + script;
    }
}
