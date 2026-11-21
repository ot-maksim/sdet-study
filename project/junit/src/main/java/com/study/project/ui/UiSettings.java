package com.study.project.ui;

import java.net.URI;
import java.time.Duration;

/** What the browser layer needs; validated here, whoever binds the values ({@code project.ui.*}). */
public record UiSettings(URI baseUrl, String profile, boolean headless, Duration timeout, int maxBrowsers) {

    public UiSettings {
        if (maxBrowsers < 1) {
            throw new IllegalArgumentException("project.ui.max-browsers must be at least 1, was " + maxBrowsers);
        }
    }
}
