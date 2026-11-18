package com.study.principles.inheritance.basetest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** The deployed environment: it exists independently of the tests, the API writes to it, the DB reads it. */
final class Stand {

    static final Map<Long, User> USERS = new ConcurrentHashMap<>();
    static final AtomicLong IDS = new AtomicLong();

    private Stand() {
    }
}
