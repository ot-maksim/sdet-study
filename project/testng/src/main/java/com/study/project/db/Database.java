package com.study.project.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.postgresql.ds.PGSimpleDataSource;

/**
 * A tiny JDBC helper: connection per call, parameters bound in order, {@link SQLException} translated with the
 * statement in the message. Run-scoped and stateless, so parallel tests share it safely. No pool: a test suite
 * issues few statements, and the SUT's pool is not ours to imitate (add HikariCP when a profile shows the need).
 *
 * <p>Table knowledge (columns, mapping) lives in repositories such as {@code users.UserRows}, never in tests.
 */
public final class Database {

    private final DataSource dataSource;
    private final String url;

    public Database(DbSettings settings) {
        PGSimpleDataSource pg = new PGSimpleDataSource();
        pg.setUrl(settings.url());
        pg.setUser(settings.user());
        pg.setPassword(settings.password().reveal());
        pg.setConnectTimeout(5);
        this.dataSource = pg;
        this.url = settings.url();
    }

    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        try (Connection c = connect(); PreparedStatement ps = prepare(c, sql, params); ResultSet rs = ps.executeQuery()) {
            List<T> rows = new ArrayList<>();
            while (rs.next()) {
                rows.add(mapper.map(rs));
            }
            return rows;
        } catch (SQLException e) {
            throw failed(sql, params, e);
        }
    }

    /** At most one row; more is a broken assumption about the data, not "take the first". */
    public <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        List<T> rows = query(sql, mapper, params);
        if (rows.size() > 1) {
            throw new DbException("Expected at most one row, got " + rows.size() + ": " + describe(sql, params));
        }
        return rows.stream().findFirst();
    }

    public int update(String sql, Object... params) {
        try (Connection c = connect(); PreparedStatement ps = prepare(c, sql, params)) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw failed(sql, params, e);
        }
    }

    private Connection connect() throws SQLException {
        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            throw new DbException("Cannot connect to " + url + ": " + e.getMessage()
                    + ". Is the stand up? docker compose up -d --wait", e);
        }
    }

    private static PreparedStatement prepare(Connection c, String sql, Object... params) throws SQLException {
        PreparedStatement ps = c.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            Object value = params[i] instanceof Instant instant ? OffsetDateTime.ofInstant(instant, ZoneOffset.UTC) : params[i];
            ps.setObject(i + 1, value);
        }
        return ps;
    }

    private static DbException failed(String sql, Object[] params, SQLException e) {
        return new DbException(describe(sql, params) + " failed: [" + e.getSQLState() + "] " + e.getMessage(), e);
    }

    private static String describe(String sql, Object[] params) {
        return sql + (params.length == 0 ? "" : " " + Arrays.toString(params));
    }
}
