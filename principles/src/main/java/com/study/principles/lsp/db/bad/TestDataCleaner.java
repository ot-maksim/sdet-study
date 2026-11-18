package com.study.principles.lsp.db.bad;

/** Runs in {@code @AfterEach}: removes everything the test created under its prefix. Trusts the {@link DbClient} contract. */
public final class TestDataCleaner {

    private final DbClient db;

    public TestDataCleaner(DbClient db) {
        this.db = db;
    }

    public int cleanUp(String prefix) {
        return db.deleteUsers(prefix);
    }
}
