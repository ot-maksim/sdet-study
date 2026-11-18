package com.study.principles.deep.good;

import java.util.Objects;

/** What the test wants to create — in domain terms, no JSON, no headers. */
public record NewUser(String name, String email, Role role) {

    public NewUser {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(role, "role");
    }

    public static NewUser user(String name, String email) {
        return new NewUser(name, email, Role.USER);
    }

    public NewUser withRole(Role role) {
        return new NewUser(name, email, role);
    }
}
