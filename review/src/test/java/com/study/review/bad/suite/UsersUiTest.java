package com.study.review.bad.suite;

import com.study.review.bad.core.BaseTest;
import com.study.review.bad.data.TestData;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The team's UI regression tests, written on the inherited framework. */
public class UsersUiTest extends BaseTest {

    @Test
    void createdUserIsListed() {
        Map<String, Object> user = TestData.randomUser();

        usersPage.open();
        usersPage.createUser((String) user.get("name"), (String) user.get("email"), 30, "GB");

        usersPage.verifyUserInTable((String) user.get("email"));
    }

    @Test
    void userCreatedThroughApiIsListed() {
        Map<String, Object> user = TestData.randomUser();

        usersPage.open();
        usersPage.createUserViaApi(user);

        usersPage.verifyUserInTable((String) user.get("email"));
    }
}
