package com.study.principles.lsp.db;

import java.util.List;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 * The {@code users} table of the stand, reduced to e-mails — the "JDBC connection" the clients wrap.
 * Primary and replica clients look at the same data (replication is instant here). {@link #seed} stands for
 * data created through another channel (the SUT's API, a previous test).
 */
public final class FakeDatabase {

    private final NavigableSet<String> users = new ConcurrentSkipListSet<>();

    public void seed(String... emails) {
        users.addAll(List.of(emails));
    }

    public List<String> select(String prefix) {
        return users.stream().filter(email -> email.startsWith(prefix)).toList();
    }

    /** @return false if the e-mail already exists (unique constraint) */
    public boolean insert(String email) {
        return users.add(email);
    }

    public int delete(String prefix) {
        List<String> matching = select(prefix);
        users.removeAll(matching);
        return matching.size();
    }
}
