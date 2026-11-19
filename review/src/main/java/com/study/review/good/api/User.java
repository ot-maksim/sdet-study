package com.study.review.good.api;

import java.time.Instant;

/** A user as the API returns it; the framework's own copy of the contract. */
public record User(long id, String name, String email, int age, String country, Instant createdAt) {
}
