package com.study.principles.lsp.db.bad;

import com.study.principles.lsp.db.FakeDatabase;

/**
 * "Staging tests must not write, they read from the replica — so: a read-only DbClient." IS-A by name only:
 * the subclass <b>strengthens the precondition</b> of the write methods to "never call me" and throws an
 * exception the base never does. Everything typed {@code DbClient} accepts it, and fails at run time.
 */
public class ReadOnlyDbClient extends DbClient {

    public ReadOnlyDbClient(FakeDatabase db) {
        super(db);
    }

    @Override
    public void insertUser(String email) {
        throw new UnsupportedOperationException("replica is read-only");
    }

    @Override
    public int deleteUsers(String prefix) {
        throw new UnsupportedOperationException("replica is read-only");
    }
}
