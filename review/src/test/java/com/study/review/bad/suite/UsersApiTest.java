package com.study.review.bad.suite;

import com.study.review.bad.core.BaseTest;
import com.study.review.bad.data.TestData;
import com.study.review.bad.steps.UserSteps;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The team's API regression tests, written on the inherited framework. */
public class UsersApiTest extends BaseTest {

    private final UserSteps steps = new UserSteps();

    @Test
    void createsUser() throws Exception {
        Map<String, Object> user = TestData.randomUser();
        user.put("email", ((String) user.get("email")).toUpperCase());
        user.put("country", "gb");

        steps.createUser(user);

        steps.responseStatusShouldBe(201);
        steps.userShouldMatchRequest();
        steps.userShouldExist();
    }

    @Test
    void rejectsUnderageUser() {
        Map<String, Object> user = TestData.randomUser();
        user.put("age", 17);

        steps.createUser(user);

        steps.responseStatusShouldBe(400);
    }

    @Test
    void createdUserIsStored() throws Exception {
        steps.createUser(TestData.randomUser());

        steps.userShouldBeInDb(db);
    }
}
