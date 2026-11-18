package com.study.principles.lsp.db.bad;

import com.study.principles.lsp.db.FakeDatabase;

import java.util.List;

/**
 * Contract (what callers rely on, written or not):
 * <ul>
 *   <li>{@code users(prefix)} returns the users whose e-mail starts with the prefix;</li>
 *   <li>after {@code insertUser(e)} the user is returned by {@code users(..)}; a duplicate throws;</li>
 *   <li>after {@code deleteUsers(p)} no user with the prefix is left; the result is how many were removed.</li>
 * </ul>
 */
public class DbClient {

    protected final FakeDatabase db;

    public DbClient(FakeDatabase db) {
        this.db = db;
    }

    public List<String> users(String prefix) {
        return db.select(prefix);
    }

    public void insertUser(String email) {
        if (!db.insert(email)) throw new IllegalStateException("duplicate e-mail: " + email);
    }

    public int deleteUsers(String prefix) {
        return db.delete(prefix);
    }
}
