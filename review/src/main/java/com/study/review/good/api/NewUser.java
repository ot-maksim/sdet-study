package com.study.review.good.api;

/**
 * A create request as the framework sees the API — its own type, not the SUT's class: a renamed field in the
 * service must break these tests, not silently follow along. {@code age} is nullable to express "missing".
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
