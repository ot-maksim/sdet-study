package com.study.sut.users;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** For running the SUT without Postgres ({@code SUT_STORAGE=memory}) and for the SUT's own tests. */
public class InMemoryUserRepository implements UserRepository {

    private final Map<Long, User> users = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public synchronized User insert(ValidUser user, Instant createdAt) {
        if (findByEmail(user.email()).isPresent()) {
            throw UserRejectedException.emailTaken(user.email());
        }
        User stored = new User(nextId++, user.name(), user.email(), user.age(), user.country(), createdAt);
        users.put(stored.id(), stored);
        return stored;
    }

    @Override
    public synchronized Optional<User> findById(long id) {
        return Optional.ofNullable(users.get(id));
    }

    @Override
    public synchronized Optional<User> findByEmail(String email) {
        return users.values().stream().filter(u -> u.email().equals(email)).findFirst();
    }

    @Override
    public synchronized List<User> findAll() {
        return List.copyOf(new ArrayList<>(users.values()));
    }

    @Override
    public synchronized boolean delete(long id) {
        return users.remove(id) != null;
    }
}
