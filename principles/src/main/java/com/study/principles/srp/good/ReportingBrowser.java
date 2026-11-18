package com.study.principles.srp.good;

import com.study.principles.srp.Browser;

/**
 * Reason to change: the <b>reporting</b> format. A decorator over {@link Browser}: every page gets step
 * reporting without a line of reporting code, and masking card numbers is decided once, here.
 */
public final class ReportingBrowser implements Browser {

    private final Browser delegate;
    private final StepReporter reporter;

    public ReportingBrowser(Browser delegate, StepReporter reporter) {
        this.delegate = delegate;
        this.reporter = reporter;
    }

    @Override
    public void open(String path) {
        reporter.step("open " + path);
        delegate.open(path);
    }

    @Override
    public boolean isVisible(String selector) {
        return delegate.isVisible(selector);
    }

    @Override
    public void type(String selector, String text) {
        reporter.step("type " + selector + " = " + mask(text));
        delegate.type(selector, text);
    }

    @Override
    public void click(String selector) {
        reporter.step("click " + selector);
        delegate.click(selector);
    }

    @Override
    public String text(String selector) {
        String text = delegate.text(selector);
        reporter.step("read " + selector + " = " + text);
        return text;
    }

    private static String mask(String text) {
        return text.matches("\\d{12,19}") ? "*".repeat(text.length() - 4) + text.substring(text.length() - 4) : text;
    }
}
