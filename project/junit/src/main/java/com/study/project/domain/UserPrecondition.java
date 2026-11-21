package com.study.project.domain;

import com.study.project.api.users.NewUser;
import com.study.project.api.users.User;

/**
 * "There is a user" — for tests where the user is a precondition, not the subject. How it gets there is an
 * implementation detail chosen by the composition root ({@code domain.user-precondition}): the test depends on
 * this abstraction, not on SQL or HTTP (DIP, principles/dip). Every user created is removed after the test.
 *
 * <p>Contract (checked by {@code UserPreconditionContract} for every implementation): the user is visible
 * through the API exactly as if it had been created through the API.
 */
public interface UserPrecondition {
    User create(NewUser user);
}
