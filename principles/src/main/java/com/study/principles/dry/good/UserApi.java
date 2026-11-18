package com.study.principles.dry.good;

import com.study.principles.dry.FakeServer;
import com.study.principles.dry.HttpResponse;

/**
 * The mechanics of talking to the users endpoint, once. DRY applies here: the path and the serialization are
 * knowledge about the app's interface, and a rename is a one-line change.
 */
public final class UserApi {

    private static final String USERS = "/api/v1/users";

    private final FakeServer http;

    public UserApi(FakeServer http) {
        this.http = http;
    }

    public HttpResponse create(NewUser user) {
        return http.post(USERS, user.json());
    }
}
