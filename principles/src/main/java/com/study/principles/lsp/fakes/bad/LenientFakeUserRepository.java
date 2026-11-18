package com.study.principles.lsp.fakes.bad;

import com.study.principles.lsp.fakes.User;
import com.study.principles.lsp.fakes.UserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The fake written in five minutes. It implements the interface — and breaks the contract twice:
 * no unique constraint (a duplicate is accepted, the first match wins on read), and it hands out the
 * <i>stored instance</i>, so a change to a returned object is "saved" without {@code update}.
 * Tests on it are green for code that fails on the real database.
 */
public final class LenientFakeUserRepository implements UserRepository {

    private final List<User> users = new ArrayList<>();

    @Override
    public User insert(User newUser) {
        User stored = new User((long) users.size() + 1, newUser.email(), newUser.name());
        users.add(stored);
        return stored;
    }

    @Override
    public void update(User user) {
        // nothing to do: callers already hold our instances
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return users.stream().filter(user -> user.email().equals(email)).findFirst();
    }
}
