package com.study.review.bad.steps;

import com.study.sut.users.NewUser;
import com.study.sut.users.UserRules;
import com.study.sut.users.ValidUser;
import java.util.Map;

/** Expected state of a user after creation: the same normalization as the service applies. */
public final class Expected {

    private Expected() {
    }

    public static ValidUser user(Map<String, Object> request) {
        return UserRules.validate(new NewUser(
                (String) request.get("name"),
                (String) request.get("email"),
                (Integer) request.get("age"),
                (String) request.get("country")));
    }
}
