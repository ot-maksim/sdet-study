package com.study.project.db.users;

import java.time.Instant;

/**
 * A row of {@code users} as stored. Deliberately not the api module's {@code User}: the table and the API
 * change for different reasons (a column rename is not an API change), and db must not depend on api.
 * The domain layer compares the two.
 */
public record UserRow(long id, String name, String email, int age, String country, Instant createdAt) {
}
