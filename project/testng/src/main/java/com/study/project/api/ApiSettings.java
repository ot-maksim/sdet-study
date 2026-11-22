package com.study.project.api;

import java.net.URI;
import java.time.Duration;

/** Where the SUT's API is and how long a call may take. */
public record ApiSettings(URI baseUrl, Duration timeout) {

    public ApiSettings {
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("project.api.timeout must be positive, was " + timeout);
        }
    }
}
