package com.study.review.good.db;

import com.study.review.good.config.Settings.DbSettings;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * The {@code users} table: the only class that knows its columns. Values are bound, never concatenated — an
 * email like {@code o'brien@example.com} is valid for the SUT and must be valid here. A connection per call,
 * closed with everything it opened. Full version: {@code framework/db}.
 */
public final class UserRows {

    private final DbSettings settings;

    public UserRows(DbSettings settings) {
        this.settings = settings;
    }

    public Optional<UserRow> findByEmail(String email) {
        String sql = "SELECT id, name, email, age, country, created_at FROM users WHERE email = ?";
        try (Connection connection = DriverManager.getConnection(settings.url(), settings.user(), settings.password().value());
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                UserRow row = new UserRow(rs.getLong("id"), rs.getString("name"), rs.getString("email"), rs.getInt("age"),
                        rs.getString("country").strip(), rs.getTimestamp("created_at").toInstant());
                if (rs.next()) {
                    throw new IllegalStateException("email is unique, but found more than one row for " + email);
                }
                return Optional.of(row);
            }
        } catch (SQLException e) {
            throw new IllegalStateException(sql + " [" + email + "] failed, SQLState " + e.getSQLState() + ": " + e.getMessage(), e);
        }
    }
}
