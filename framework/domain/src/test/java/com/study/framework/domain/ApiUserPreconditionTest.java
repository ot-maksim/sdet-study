package com.study.framework.domain;

import com.study.framework.api.users.UserApi;
import com.study.framework.core.lifecycle.Cleanup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(StandRoot.class)
class ApiUserPreconditionTest implements UserPreconditionContract {

    private UserApi api;

    @BeforeEach
    void setUp(UserApi api) {
        this.api = api;
    }

    @Override
    public UserPrecondition precondition(Cleanup cleanup) {
        return new ApiUserPrecondition(api, cleanup);
    }
}
