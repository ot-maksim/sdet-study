package com.study.principles.lsp.fakes;

/** Code under test, <b>with a bug</b>: it renames the loaded object and forgets {@code users.update(user)}. */
public final class ProfileService {

    private final UserRepository users;

    public ProfileService(UserRepository users) {
        this.users = users;
    }

    public void rename(String email, String newName) {
        User user = users.findByEmail(email).orElseThrow();
        user.rename(newName);
        // BUG: users.update(user) is missing — only a repository that returns copies can show it
    }
}
