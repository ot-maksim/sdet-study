package com.study.framework.api.users;

import java.time.Instant;

/** Response DTO: a user as the API returns it. Unknown fields are ignored (tolerant reader). */
public record User(long id, String name, String email, int age, String country, Instant createdAt) {
}
