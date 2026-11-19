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

    /**
     * For values that come from another configuration system (stage 4: Spring Boot properties, a Testcontainers
     * container's generated password); {@link ConfigReader#secret} stays the way for this framework's own config.
     */
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
