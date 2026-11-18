package com.study.principles.deep.good;

/** A user as the SUT returns it. */
public record User(long id, String name, String email, Role role) {
}
