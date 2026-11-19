package com.study.framework.api;

import com.study.framework.core.config.ConfigReader;
import java.net.URI;
import java.time.Duration;

/** The api module's keys, read once per run: {@code config.bind(ApiSettings::from)}. */
public record ApiSettings(URI baseUrl, Duration timeout) {

    public ApiSettings {
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("api.timeout must be positive, was " + timeout);
        }
    }

    public static ApiSettings from(ConfigReader config) {
        return new ApiSettings(config.uri("api.base-url"), config.duration("api.timeout"));
    }
}
