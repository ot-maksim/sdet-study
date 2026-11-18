package com.study.principles.inheritance.basetest.bad.split;

import com.study.principles.inheritance.basetest.Db;
import com.study.principles.inheritance.basetest.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public abstract class DbBaseTest extends ApiBaseTest {

    protected Db db;

    @BeforeEach
    void dbSetUp() {
        db = Db.connect();
    }

    @AfterEach
    void dbTearDown() {
        db.close();
    }

    protected void assertStored(User expected) {
        if (db.findUser(expected.id()).filter(expected::equals).isEmpty()) {
            throw new AssertionError("not in DB: " + expected);
        }
    }
}
