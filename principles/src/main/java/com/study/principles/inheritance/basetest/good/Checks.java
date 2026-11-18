package com.study.principles.inheritance.basetest.good;

import com.study.principles.inheritance.basetest.Db;
import com.study.principles.inheritance.basetest.User;

/**
 * The former "utility drawer" of {@code BaseTest}: helpers that need no state are static functions
 * (or AssertJ custom assertions) — reachable from any test without extending anything.
 */
public final class Checks {

    private Checks() {
    }

    public static void assertStored(Db db, User expected) {
        if (db.findUser(expected.id()).filter(expected::equals).isEmpty()) {
            throw new AssertionError("not in DB: " + expected);
        }
    }
}
