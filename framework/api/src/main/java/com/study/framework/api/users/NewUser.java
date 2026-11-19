package com.study.framework.api.users;

/**
 * Request DTO of {@code POST /api/users}. Immutable; tests derive variants with withers:
 * {@code users.valid().withAge(17)} — the test states only what differs (DAMP, principles/dry).
 * Fields are nullable on purpose: a {@code null} is sent as a missing value, which is a test case too.
 *
 * <p>Withers instead of a separate Builder class: four fields, always a valid starting point
 * ({@link NewUsers#valid()}), nothing to accumulate before {@code build()}.
 */
public record NewUser(String name, String email, Integer age, String country) {

    public NewUser withName(String name) {
        return new NewUser(name, email, age, country);
    }

    public NewUser withEmail(String email) {
        return new NewUser(name, email, age, country);
    }

    public NewUser withAge(Integer age) {
        return new NewUser(name, email, age, country);
    }

    public NewUser withCountry(String country) {
        return new NewUser(name, email, age, country);
    }
}
