package com.study.principles.concurrency.events.ui;

import java.time.Duration;
import java.util.Objects;

/**
 * Web-first assertions, shaped like Playwright's {@code assertThat(locator).hasText(..)}: they <b>retry</b> until the
 * condition holds or the timeout runs out, re-checking on every DOM change. Compare with a snapshot
 * ({@code page.textContent(..)} + a plain assertion), which checks once, whenever the test happens to get there.
 */
public final class Expect {

    private Expect() {
    }

    public static LocatorAssert that(FakePage page, String selector) {
        return new LocatorAssert(page, selector, page.defaultTimeout());
    }

    public static final class LocatorAssert {

        private final FakePage page;
        private final String selector;
        private final Duration timeout;

        private LocatorAssert(FakePage page, String selector, Duration timeout) {
            this.page = page;
            this.selector = selector;
            this.timeout = timeout;
        }

        public LocatorAssert withTimeout(Duration timeout) {
            return new LocatorAssert(page, selector, timeout);
        }

        public void hasText(String expected) {
            check(() -> Objects.equals(expected, page.textContent(selector)), "to have text \"" + expected + "\"");
        }

        public void isVisible() {
            check(() -> page.isVisible(selector), "to be visible");
        }

        public void isHidden() {
            check(() -> !page.isVisible(selector), "to be hidden");
        }

        private void check(java.util.function.BooleanSupplier condition, String expectation) {
            if (!page.awaitDom(condition, timeout)) {
                String last = page.textContent(selector);
                throw new AssertionError("Locator " + selector + " expected " + expectation + " within " + timeout
                        + ", but was " + (last == null ? "not on the page" : "\"" + last + "\""));
            }
        }
    }
}
