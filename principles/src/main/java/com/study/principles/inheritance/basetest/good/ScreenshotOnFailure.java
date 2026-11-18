package com.study.principles.inheritance.basetest.good;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;

/**
 * A cross-cutting behaviour as its own extension. It cooperates with {@link BrowserExtension} without
 * inheriting from it: if the test has a browser, take a screenshot; if not (API test), do nothing —
 * never launch a browser just to photograph it.
 * <p>
 * An exception handler and not a {@code TestWatcher}: it runs while the browser is still open.
 */
public final class ScreenshotOnFailure implements TestExecutionExceptionHandler {

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable failure) throws Throwable {
        BrowserExtension.started(context)
                .filter(browser -> !browser.isClosed())
                .ifPresent(browser -> browser.screenshot(context.getRequiredTestMethod().getName()));
        throw failure;
    }
}
