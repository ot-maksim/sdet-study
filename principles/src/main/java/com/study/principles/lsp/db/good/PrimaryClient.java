package com.study.principles.lsp.db.good;

import com.study.principles.lsp.db.FakeDatabase;

import java.util.List;

public final class PrimaryClient implements DbWriter {

    private final FakeDatabase db;

    public PrimaryClient(FakeDatabase db) {
        this.db = db;
    }

    @Override
    public List<String> users(String prefix) {
        return db.select(prefix);
    }

    @Override
    public void insertUser(String email) {
        if (!db.insert(email)) throw new IllegalStateException("duplicate e-mail: " + email);
    }

    @Override
    public int deleteUsers(String prefix) {
        return db.delete(prefix);
    }
}
