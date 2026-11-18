package com.study.principles.lsp.db.good;

import com.study.principles.lsp.db.FakeDatabase;

import java.util.List;

/** Read-only by type: there is no write method to refuse. */
public final class ReplicaClient implements DbReader {

    private final FakeDatabase db;

    public ReplicaClient(FakeDatabase db) {
        this.db = db;
    }

    @Override
    public List<String> users(String prefix) {
        return db.select(prefix);
    }
}
