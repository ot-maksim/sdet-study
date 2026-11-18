package com.study.principles.coupling.bad.locator;

import com.study.principles.coupling.Browser;

/**
 * SMELL: service locator / global holder. "Fixes" {@code new} inside pages by moving the browser into a
 * static field everybody reads. Pages now share a browser — but the dependency became invisible:
 * {@code new LoginPage()} compiles and constructs fine, and fails later, on the first action.
 * <p>
 * The {@code ThreadLocal} flavour of the same holder and its thread problems are in
 * {@code parallel.threadlocal.DriverManager}; here the point is the hidden dependency and the leftover state.
 */
public final class DriverManager {

    private static Browser browser;

    private DriverManager() {
    }

    public static void set(Browser b) {
        browser = b;
    }

    public static Browser get() {
        return browser;
    }
}
