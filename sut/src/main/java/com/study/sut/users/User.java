package com.study.sut.users;

import java.time.Instant;

/** A stored user. {@code email} is lower-cased, {@code createdAt} has microsecond precision (Postgres). */
public record User(long id, String name, String email, int age, String country, Instant createdAt) {
}
