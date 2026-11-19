package com.study.framework.core.config;

/**
 * A value that must not end up in logs, reports or assertion messages. {@link #toString()} is masked;
 * the only way to the value is the explicit {@link #reveal()}, easy to find in a review.
 */
public final class Secret {

    private final String value;

    Secret(String value) {
        this.value = value;
    }

    public String reveal() {
        return value;
    }

    @Override
    public String toString() {
        return "****";
    }
}
