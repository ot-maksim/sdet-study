package com.study.principles.lsp.db.bad.patched;

import com.study.principles.lsp.db.bad.DbClient;
import com.study.principles.lsp.db.bad.ReadOnlyDbClient;

/**
 * The usual patch for an LSP violation: the caller learns the subtypes. It does not throw any more — and it
 * still does not clean up (the data stays, as with the silent client); {@code SilentReadOnlyDbClient} slips
 * through the check; every new subtype is another {@code instanceof} in every caller.
 */
public final class TestDataCleaner {

    private final DbClient db;

    public TestDataCleaner(DbClient db) {
        this.db = db;
    }

    public int cleanUp(String prefix) {
        if (db instanceof ReadOnlyDbClient) return 0;   // "replica: nothing to clean"
        return db.deleteUsers(prefix);
    }
}
