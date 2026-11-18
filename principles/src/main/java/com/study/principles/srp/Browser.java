package com.study.principles.srp;

/** The slice of a Playwright {@code Page} the kata needs. */
public interface Browser {

    void open(String path);

    boolean isVisible(String selector);

    void type(String selector, String text);

    void click(String selector);

    String text(String selector);
}
