package com.study.principles.lsp.fakes;

/** Code under test: relies on the repository's unique constraint (no check-then-insert race). */
public final class RegistrationService {

    public enum Result { REGISTERED, ALREADY_REGISTERED }

    private final UserRepository users;

    public RegistrationService(UserRepository users) {
        this.users = users;
    }

    public Result register(String email, String name) {
        try {
            users.insert(new User(email, name));
            return Result.REGISTERED;
        } catch (DuplicateEmailException e) {
            return Result.ALREADY_REGISTERED;
        }
    }
}
