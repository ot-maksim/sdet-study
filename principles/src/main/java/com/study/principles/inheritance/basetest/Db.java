package com.study.principles.inheritance.basetest;

import java.util.Optional;

/** Fake JDBC connection to the stand's database. */
public final class Db implements AutoCloseable {

    private boolean closed;

    private Db() {
    }

    public static Db connect() {
        Startups.record("db connected");
        return new Db();
    }

    public Optional<User> findUser(long id) {
        if (closed) throw new IllegalStateException("connection is closed");
        return Optional.ofNullable(Stand.USERS.get(id));
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        Startups.record("db closed");
    }
}
