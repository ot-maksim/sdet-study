package com.study.review.good.steps;

import com.study.review.good.api.NewUser;
import com.study.review.good.api.User;
import com.study.review.good.api.UsersClient;
import com.study.review.good.data.Cleanup;
import com.study.review.good.data.TestUsers;

/**
 * Steps return what they made; the test holds it in a local variable. No shared context map: what a step needs
 * is its parameter, so the compiler checks the order and the types, and parallel tests cannot see each other.
 */
public final class UserSteps {

    private final UsersClient api;
    private final TestUsers data;
    private final Cleanup cleanup;

    public UserSteps(UsersClient api, TestUsers data, Cleanup cleanup) {
        this.api = api;
        this.data = data;
        this.cleanup = cleanup;
    }

    public NewUser aNewUser() {
        return data.aUser();
    }

    /** A user that exists for this test and is gone after it. */
    public User existing() {
        return create(data.aUser());
    }

    public User create(NewUser user) {
        User created = api.create(user);
        cleanup.register("delete user " + created.id(), () -> api.deleteIfExists(created.id()));
        return created;
    }

    /** For users created through another channel (the page): removed after the test by their email. */
    public void removeAfterTest(String email) {
        cleanup.register("delete user " + email, () -> api.findByEmail(email).ifPresent(u -> api.deleteIfExists(u.id())));
    }
}
