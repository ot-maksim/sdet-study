package com.study.principles.lsp.db.good;

/** Only reads, so it takes a {@link DbReader} — and works with the replica and the primary alike. */
public final class UserChecks {

    private final DbReader db;

    public UserChecks(DbReader db) {
        this.db = db;
    }

    public void assertExists(String email) {
        if (!db.users(email).contains(email)) throw new AssertionError("user not in DB: " + email);
    }
}
