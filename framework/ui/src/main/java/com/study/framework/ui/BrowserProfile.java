package com.study.framework.ui;

import com.microsoft.playwright.Browser;
import java.util.function.Supplier;

/**
 * How a browser context looks to the page: viewport, touch, user agent. A profile changes only the
 * <b>environment</b> of the page, never page objects: they react to what the page shows (a collapsed form),
 * not to which device they think they are on.
 */
public record BrowserProfile(String name, Supplier<Browser.NewContextOptions> contextOptions) {
}
