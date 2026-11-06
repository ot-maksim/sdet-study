package com.study.principles.dip.bad;

import com.study.principles.dip.User;

/** Low-level detail. In real life: Connection, PreparedStatement, INSERT INTO users... */
public class JdbcUserCreator {
    private static long seq = 1;

    public User insertUserRow(String jdbcUrl, String name) {
        System.out.println("  [JDBC " + jdbcUrl + "] INSERT INTO users(name) VALUES('" + name + "')");
        return new User(seq++, name);
    }
}
