package com.study.review.good.db;

import java.time.Instant;

/** A row of {@code users}; typed, so a renamed column fails in one mapper, not in casts across tests. */
public record UserRow(long id, String name, String email, int age, String country, Instant createdAt) {
}
