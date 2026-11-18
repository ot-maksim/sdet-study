package com.study.principles.lsp.fakes;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stand-in for the real JDBC repository (stage 2): data lives as <i>rows</i>, so every read maps a row into
 * a new object — copies for free, like a {@code ResultSet}. The unique index on e-mail is a second map.
 */
public final class JdbcLikeUserRepository implements UserRepository {

    private final Map<Long, Map<String, String>> rows = new ConcurrentHashMap<>();
    private final Map<String, Long> uniqueEmail = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public User insert(User newUser) {
        long id = sequence.incrementAndGet();
        if (uniqueEmail.putIfAbsent(newUser.email(), id) != null) throw new DuplicateEmailException(newUser.email());
        rows.put(id, Map.of("email", newUser.email(), "name", newUser.name()));
        return read(id);
    }

    @Override
    public void update(User user) {
        if (!rows.containsKey(user.id())) throw new IllegalArgumentException("no row with id " + user.id());
        rows.put(user.id(), Map.of("email", user.email(), "name", user.name()));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(uniqueEmail.get(email)).map(this::read);
    }

    private User read(long id) {
        Map<String, String> row = rows.get(id);
        return new User(id, row.get("email"), row.get("name"));
    }
}
