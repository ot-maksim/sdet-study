package com.study.sut.users;

/**
 * A create-user request as it arrives from a client (JSON body or HTML form).
 * Fields are nullable on purpose: a missing field is a validation error, not a parse error.
 */
public record NewUser(String name, String email, Integer age, String country) {
}
