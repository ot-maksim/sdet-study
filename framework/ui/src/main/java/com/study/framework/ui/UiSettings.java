package com.study.framework.ui;

import com.study.framework.core.config.ConfigReader;
import java.net.URI;
import java.time.Duration;

/** The ui module's keys: {@code config.bind(UiSettings::from)}. */
public record UiSettings(URI baseUrl, String profile, boolean headless, Duration timeout, int maxBrowsers) {

    public UiSettings {
        if (maxBrowsers < 1) {
            throw new IllegalArgumentException("ui.max-browsers must be at least 1, was " + maxBrowsers);
        }
    }

    public static UiSettings from(ConfigReader config) {
        return new UiSettings(config.uri("ui.base-url"), config.string("ui.profile"), config.bool("ui.headless"),
                config.duration("ui.timeout"), config.integer("ui.max-browsers"));
    }
}
