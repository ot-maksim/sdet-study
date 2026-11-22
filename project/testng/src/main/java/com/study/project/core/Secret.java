package com.study.project.core;

/**
 * A value that must not end up in logs, reports or assertion messages. {@link #toString()} is masked;
 * the only way to the value is the explicit {@link #reveal()}, easy to find in a review.
 * Configuration binds passwords straight into it ({@code ProjectConfiguration.secretConverter}).
 */
public final class Secret {

    private final String value;

    private Secret(String value) {
        this.value = value;
    }

    public static Secret of(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("a secret must not be blank");
        }
        return new Secret(value);
    }

    public String reveal() {
        return value;
    }

    @Override
    public String toString() {
        return "****";
    }
}
