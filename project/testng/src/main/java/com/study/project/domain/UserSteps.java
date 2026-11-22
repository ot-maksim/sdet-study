package com.study.project.domain;

import com.study.project.api.ApiResponse;
import com.study.project.api.users.NewUser;
import com.study.project.api.users.NewUsers;
import com.study.project.api.users.User;
import com.study.project.api.users.UserApi;
import com.study.project.core.lifecycle.Cleanup;
import com.study.project.ui.users.ShownUser;
import com.study.project.ui.users.UserForm;
import com.study.project.ui.users.UsersScreen;
import java.util.function.UnaryOperator;

/**
 * Business steps about users — a facade over api, db and ui for test scenarios. Two kinds, deliberately:
 * <ul>
 *   <li><b>preconditions</b> ({@link #existing}): the channel does not matter, the root picks it;</li>
 *   <li><b>actions under test</b> ({@link #registerThroughUi}, {@link #registerThroughApi}): the channel is the
 *       point of the test, so it is in the name.</li>
 * </ul>
 * Everything created is cleaned up after the test, whichever channel created it. Steps return values;
 * checks stay in the test or in {@link UserChecks} (principles/kiss: no "verify" flags).
 */
public final class UserSteps {

    private final UserPrecondition precondition;
    private final NewUsers newUsers;
    private final UserApi api;
    private final Cleanup cleanup;

    public UserSteps(UserPrecondition precondition, NewUsers newUsers, UserApi api, Cleanup cleanup) {
        this.precondition = precondition;
        this.newUsers = newUsers;
        this.api = api;
        this.cleanup = cleanup;
    }

    /** A valid user that just exists; fields nobody cares about. */
    public User existing() {
        return precondition.create(newUsers.valid());
    }

    /** {@code users.existing(u -> u.withCountry("GB"))}: the test states only what matters. */
    public User existing(UnaryOperator<NewUser> customize) {
        return precondition.create(customize.apply(newUsers.valid()));
    }

    /** A fresh valid request to act with. */
    public NewUser aNewUser() {
        return newUsers.valid();
    }

    public User registerThroughApi(NewUser user) {
        User created = api.create(user);
        cleanup.defer("delete user " + created.id(), () -> api.deleteIfExists(created.id()));
        return created;
    }

    /** For negative tests: nothing is expected to be created, but if the SUT did create it, it is removed. */
    public ApiResponse tryRegisterThroughApi(NewUser user) {
        cleanup.defer("delete user " + user.email() + " if the SUT created it",
                () -> api.findByEmail(user.email()).ifPresent(u -> api.deleteIfExists(u.id())));
        return api.tryCreate(user);
    }

    /** Through the form on the given screen; cleanup finds the user by email (the page does not return the id to the SUT's API). */
    public ShownUser registerThroughUi(UsersScreen screen, NewUser user) {
        cleanup.defer("delete user " + user.email() + " created through the UI",
                () -> api.findByEmail(user.email()).ifPresent(u -> api.deleteIfExists(u.id())));
        return screen.create(new UserForm(user.name(), user.email(), String.valueOf(user.age()), user.country()));
    }
}
