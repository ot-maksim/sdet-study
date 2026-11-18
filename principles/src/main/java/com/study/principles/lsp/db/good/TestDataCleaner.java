package com.study.principles.lsp.db.good;

/**
 * Asks for what it really needs: a writer. {@code new TestDataCleaner(new ReplicaClient(db))} does not compile —
 * the staging question ("can we clean up there?") is answered when the graph is wired, not in {@code @AfterEach}.
 */
public final class TestDataCleaner {

    private final DbWriter db;

    public TestDataCleaner(DbWriter db) {
        this.db = db;
    }

    public int cleanUp(String prefix) {
        return db.deleteUsers(prefix);
    }
}
