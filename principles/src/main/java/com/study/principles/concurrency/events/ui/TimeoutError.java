package com.study.principles.concurrency.events.ui;

/** Thrown when a wait runs out of time, like Playwright's {@code com.microsoft.playwright.TimeoutError}. */
public final class TimeoutError extends RuntimeException {

    public TimeoutError(String message) {
        super(message);
    }
}
