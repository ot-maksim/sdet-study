package com.study.principles.lsp.fakes.good;

import com.study.principles.lsp.fakes.DuplicateEmailException;
import com.study.principles.lsp.fakes.User;
import com.study.principles.lsp.fakes.UserRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A fake that honours the contract: unique e-mail, copies in and out. Still a fake — fast, in memory, no
 * Postgres — but substitutable: the same {@code UserRepositoryContract} passes for it and for the real one.
 */
public final class InMemoryUserRepository implements UserRepository {

    private final Map<String, User> byEmail = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    @Override
    public User insert(User newUser) {
        User stored = new User(sequence.incrementAndGet(), newUser.email(), newUser.name());
        if (byEmail.putIfAbsent(stored.email(), stored) != null) throw new DuplicateEmailException(stored.email());
        return copy(stored);
    }

    @Override
    public void update(User user) {
        User current = byEmail.get(user.email());
        if (current == null || !current.id().equals(user.id())) {
            throw new IllegalArgumentException("no user with id " + user.id());
        }
        byEmail.put(user.email(), copy(user));
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(byEmail.get(email)).map(InMemoryUserRepository::copy);
    }

    private static User copy(User user) {
        return new User(user.id(), user.email(), user.name());
    }
}
