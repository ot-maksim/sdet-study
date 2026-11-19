package com.study.framework.domain;

import com.study.framework.core.lifecycle.Cleanup;
import com.study.framework.db.users.UserRows;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(StandRoot.class)
class DbUserPreconditionTest implements UserPreconditionContract {

    private UserRows rows;

    @BeforeEach
    void setUp(UserRows rows) {
        this.rows = rows;
    }

    @Override
    public UserPrecondition precondition(Cleanup cleanup) {
        return new DbUserPrecondition(rows, Clock.systemUTC(), cleanup);
    }
}
