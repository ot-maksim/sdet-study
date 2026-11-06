package com.study.principles.dip.good;

import com.study.principles.dip.User;

/** Detail #1: creates the user via JDBC. Config comes through the constructor (DI). */
public class DbUserPrecondition implements UserPrecondition {
    private final String jdbcUrl;
    private long seq = 100;

    public DbUserPrecondition(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
    }

    @Override
    public User existingUser(String name) {
        System.out.println("  [JDBC " + jdbcUrl + "] INSERT INTO users(name) VALUES('" + name + "')");
        return new User(seq++, name);
    }
}
