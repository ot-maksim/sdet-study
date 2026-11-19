package com.study.framework.domain;

import com.study.framework.api.users.NewUser;
import com.study.framework.api.users.User;
import com.study.framework.api.users.UserApi;
import com.study.framework.core.lifecycle.Cleanup;

/** Through the SUT's API: every rule and side effect of the real create applies. The default. */
public final class ApiUserPrecondition implements UserPrecondition {

    private final UserApi api;
    private final Cleanup cleanup;

    public ApiUserPrecondition(UserApi api, Cleanup cleanup) {
        this.api = api;
        this.cleanup = cleanup;
    }

    @Override
    public User create(NewUser user) {
        User created = api.create(user);
        cleanup.defer("delete user " + created.id() + " (" + created.email() + ")", () -> api.deleteIfExists(created.id()));
        return created;
    }
}
