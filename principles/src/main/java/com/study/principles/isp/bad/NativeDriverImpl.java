package com.study.principles.isp.bad;

import com.study.principles.isp.FakeScreen;

/** Appium-backed. Five of the 13 methods are web concepts — stubs that throw. */
public final class NativeDriverImpl implements Driver {

    private final FakeScreen screen;
    private String installed;

    public NativeDriverImpl(FakeScreen screen) {
        this.screen = screen;
    }

    @Override
    public void open(String url) {
        throw new UnsupportedOperationException("open(url) is not supported on native");
    }

    @Override
    public String currentUrl() {
        throw new UnsupportedOperationException("currentUrl is not supported on native");
    }

    @Override
    public void click(String id) {
        tap(id);
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
        throw new UnsupportedOperationException("executeScript is not supported on native");
    }

    @Override
    public void addCookie(String name, String value) {
        throw new UnsupportedOperationException("cookies are not supported on native");
    }

    @Override
    public void clearCookies() {
        throw new UnsupportedOperationException("cookies are not supported on native");
    }

    @Override
    public void tap(String id) {
        screen.press(id);
    }

    @Override
    public void swipe(String direction) {
        if (direction.equals("left")) screen.nextSlide();
    }

    @Override
    public void installApp(String path) {
        installed = path;
    }

    @Override
    public void launchApp(String bundleId) {
        if (installed == null) throw new IllegalStateException("app is not installed");
    }
}
