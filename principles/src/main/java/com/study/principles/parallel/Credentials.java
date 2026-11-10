package com.study.principles.parallel;

import java.util.Objects;

/**
 * Login and password of a test account. Immutable, so it can be shared freely between tests.
 * {@code toString} hides the password: it ends up in test names and failure messages.
 */
public record Credentials(String username, String password) {

    public Credentials {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
    }

    @Override
    public String toString() {
        return "Credentials[" + username + ", ***]";
    }
}
