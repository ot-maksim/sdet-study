package com.study.principles.isp.good;

import com.study.principles.isp.FakeScreen;

/** Implements only what a device can do — no stubs. */
public final class AppiumDevice implements MobileApp {

    private final FakeScreen screen;
    private String installed;

    public AppiumDevice(FakeScreen screen) {
        this.screen = screen;
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
