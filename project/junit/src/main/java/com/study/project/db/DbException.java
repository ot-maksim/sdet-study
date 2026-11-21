package com.study.project.db;

/** A failed statement, with the SQL and its parameters in the message. */
public class DbException extends RuntimeException {

    public DbException(String message, Throwable cause) {
        super(message, cause);
    }

    public DbException(String message) {
        super(message);
    }
}
