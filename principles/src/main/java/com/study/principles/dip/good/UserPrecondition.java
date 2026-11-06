package com.study.principles.dip.good;

import com.study.principles.dip.User;

/**
 * The abstraction. Key point: it is OWNED by the high level (tests) and speaks
 * the test's language ("I need an existing user"), not the language of JDBC/HTTP.
 * Details (Db/Api/InMemory) depend on it — the dependency arrow is inverted.
 */
public interface UserPrecondition {
    User existingUser(String name);
}
