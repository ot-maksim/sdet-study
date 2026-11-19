package com.study.framework.db.users;

import com.study.framework.db.Database;
import com.study.framework.db.RowMapper;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * The {@code users} table from the test's side: read to verify what the SUT stored, write to seed
 * preconditions fast. The only class that knows the column names.
 *
 * <p><b>Seeding bypasses the application.</b> {@link #insert} skips validation and normalization: it can create
 * rows the SUT never would (an upper-case email, a country "XX"). Seed only data the SUT could have produced,
 * or the test checks a state that does not exist in production. Constraints in the schema (unique email,
 * age &gt;= 18) still apply — a violation is a {@code DbException} with the SQL state.
 */
public final class UserRows {

    private static final String COLUMNS = "id, name, email, age, country, created_at";
    private static final RowMapper<UserRow> ROW = rs -> new UserRow(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("email"),
            rs.getInt("age"),
            rs.getString("country").strip(),   // char(2)
            rs.getObject("created_at", OffsetDateTime.class).toInstant());

    private final Database db;

    public UserRows(Database db) {
        this.db = db;
    }

    public Optional<UserRow> findById(long id) {
        return db.queryOne("SELECT " + COLUMNS + " FROM users WHERE id = ?", ROW, id);
    }

    public Optional<UserRow> findByEmail(String email) {
        return db.queryOne("SELECT " + COLUMNS + " FROM users WHERE email = ?", ROW, email);
    }

    public UserRow insert(NewUserRow row) {
        Instant createdAt = row.createdAt();
        return db.queryOne("INSERT INTO users (name, email, age, country, created_at) VALUES (?, ?, ?, ?, ?) RETURNING " + COLUMNS,
                ROW, row.name(), row.email(), row.age(), row.country(), createdAt).orElseThrow();
    }

    /** @return {@code true} if a row was deleted; idempotent, for cleanup */
    public boolean delete(long id) {
        return db.update("DELETE FROM users WHERE id = ?", id) > 0;
    }
}
