package com.study.principles.lsp.db.good;

/**
 * A reader that can also write — a real IS-A: a writer is usable everywhere a reader is.
 * Contract: after {@code insertUser(e)} the user is visible, a duplicate throws; after {@code deleteUsers(p)}
 * none is left and the result is the number removed.
 */
public interface DbWriter extends DbReader {

    void insertUser(String email);

    int deleteUsers(String prefix);
}
