package com.study.principles.isp;

import com.study.principles.isp.bad.Driver;

import java.util.ArrayList;
import java.util.List;

/**
 * The fake you have to write to unit-test {@code bad.ScreenshotOnFailure}, which calls one method.
 * Twelve of the thirteen are noise — and every new {@link Driver} method breaks this file.
 * (Mockito hides the noise, not the dependency.) Compare the lambda in {@code IspTest.Good}.
 */
final class FakeDriverForScreenshots implements Driver {

    final List<String> taken = new ArrayList<>();

    @Override
    public String screenshot(String name) {
        taken.add(name);
        return name;
    }

    // ---- not used by the client under test ----------------------------------------------------------

    @Override
    public void open(String url) {
        throw new AssertionError("not used");
    }

    @Override
    public String currentUrl() {
        throw new AssertionError("not used");
    }

    @Override
    public void click(String id) {
        throw new AssertionError("not used");
    }

    @Override
    public void type(String id, String text) {
        throw new AssertionError("not used");
    }

    @Override
    public String text(String id) {
        throw new AssertionError("not used");
    }

    @Override
    public Object executeScript(String script) {
        throw new AssertionError("not used");
    }

    @Override
    public void addCookie(String name, String value) {
        throw new AssertionError("not used");
    }

    @Override
    public void clearCookies() {
        throw new AssertionError("not used");
    }

    @Override
    public void tap(String id) {
        throw new AssertionError("not used");
    }

    @Override
    public void swipe(String direction) {
        throw new AssertionError("not used");
    }

    @Override
    public void installApp(String path) {
        throw new AssertionError("not used");
    }

    @Override
    public void launchApp(String bundleId) {
        throw new AssertionError("not used");
    }
}
