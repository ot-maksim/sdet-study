package com.study.review.bad.core;

import com.microsoft.playwright.Page;
import java.nio.file.Path;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;

/** Saves a screenshot to {@code target/screenshots} when a test fails. */
public class ScreenshotOnFailure implements TestExecutionExceptionHandler {

    @Override
    public void handleTestExecutionException(ExtensionContext context, Throwable failure) throws Throwable {
        if (Driver.isStarted()) {
            try {
                Path file = Path.of("target", "screenshots",
                        context.getRequiredTestClass().getSimpleName() + "-" + context.getRequiredTestMethod().getName() + ".png");
                Driver.getPage().screenshot(new Page.ScreenshotOptions().setPath(file).setTimeout(5_000));
            } catch (Exception e) {
                failure.addSuppressed(e);
            }
        }
        throw failure;
    }
}
