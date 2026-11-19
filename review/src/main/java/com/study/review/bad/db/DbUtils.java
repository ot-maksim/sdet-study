package com.study.review.bad.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/** Database helpers for checks. */
public final class DbUtils {

    private DbUtils() {
    }

    public static Map<String, Object> findUserByEmail(Connection connection, String email) throws SQLException {
        Statement statement = connection.createStatement();
        ResultSet rs = statement.executeQuery("SELECT * FROM users WHERE email = '" + email + "'");
        Map<String, Object> row = new HashMap<>();
        if (rs.next()) {
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                row.put(meta.getColumnName(i), rs.getObject(i));
            }
        }
        return row;
    }

    public static int countUsers(Connection connection) throws SQLException {
        ResultSet rs = connection.createStatement().executeQuery("SELECT count(*) FROM users");
        rs.next();
        return rs.getInt(1);
    }
}
