package com.study.sut.db;

import com.study.sut.users.User;
import com.study.sut.users.UserRejectedException;
import com.study.sut.users.UserRepository;
import com.study.sut.users.ValidUser;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

/** Plain JDBC, one connection per call. Uniqueness of email is enforced by the database. */
public class JdbcUserRepository implements UserRepository {

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String COLUMNS = "id, name, email, age, country, created_at";

    private final DataSource dataSource;

    public JdbcUserRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public User insert(ValidUser user, Instant createdAt) {
        String sql = "INSERT INTO users (name, email, age, country, created_at) VALUES (?, ?, ?, ?, ?) RETURNING " + COLUMNS;
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, user.name());
            ps.setString(2, user.email());
            ps.setInt(3, user.age());
            ps.setString(4, user.country());
            ps.setObject(5, OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return map(rs);
            }
        } catch (SQLException e) {
            if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
                throw UserRejectedException.emailTaken(user.email());
            }
            throw new IllegalStateException("Insert failed", e);
        }
    }

    @Override
    public Optional<User> findById(long id) {
        return queryOne("SELECT " + COLUMNS + " FROM users WHERE id = ?", ps -> ps.setLong(1, id));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return queryOne("SELECT " + COLUMNS + " FROM users WHERE email = ?", ps -> ps.setString(1, email));
    }

    @Override
    public List<User> findAll() {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS + " FROM users ORDER BY id");
             ResultSet rs = ps.executeQuery()) {
            List<User> users = new ArrayList<>();
            while (rs.next()) {
                users.add(map(rs));
            }
            return users;
        } catch (SQLException e) {
            throw new IllegalStateException("Query failed", e);
        }
    }

    @Override
    public boolean delete(long id) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM users WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Delete failed", e);
        }
    }

    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private Optional<User> queryOne(String sql, Binder binder) {
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Query failed", e);
        }
    }

    private static User map(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("email"),
                rs.getInt("age"),
                rs.getString("country"),
                rs.getObject("created_at", OffsetDateTime.class).toInstant());
    }
}
