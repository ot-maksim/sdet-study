package com.study.principles.isp.bad;

/**
 * One interface for everything a test might ever do on any platform. Every implementation must answer all
 * 13 methods, every client depends on all 13, every fake must stub all 13 — and adding a 14th recompiles
 * and touches all of them.
 */
public interface Driver {

    // web navigation
    void open(String url);

    String currentUrl();

    // interaction — the part everybody needs
    void click(String id);

    void type(String id, String text);

    String text(String id);

    String screenshot(String name);

    // web only
    Object executeScript(String script);

    void addCookie(String name, String value);

    void clearCookies();

    // mobile only
    void tap(String id);

    void swipe(String direction);

    void installApp(String path);

    void launchApp(String bundleId);
}
