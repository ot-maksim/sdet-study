package com.study.framework.db.users;

import java.time.Instant;

/** What {@link UserRows#insert} writes; the database assigns the id. */
public record NewUserRow(String name, String email, int age, String country, Instant createdAt) {
}
