package com.study.principles.lsp.db.bad;

import com.study.principles.lsp.db.FakeDatabase;

/**
 * The "fix" after {@link ReadOnlyDbClient} broke the cleanup: writes become no-ops. It <b>weakens the
 * postcondition</b> — after {@code deleteUsers} the users are still there — and nobody is told. The worst kind
 * of LSP violation: everything is green, and the next test finds the previous test's data.
 */
public class SilentReadOnlyDbClient extends DbClient {

    public SilentReadOnlyDbClient(FakeDatabase db) {
        super(db);
    }

    @Override
    public void insertUser(String email) {
        // replica ignores writes
    }

    @Override
    public int deleteUsers(String prefix) {
        return 0;
    }
}
