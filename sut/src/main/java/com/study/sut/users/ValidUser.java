package com.study.sut.users;

/** A request that passed {@link UserRules}: trimmed, normalized, every field present. */
public record ValidUser(String name, String email, int age, String country) {
}
