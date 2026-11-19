package com.study.review.bad.data;

import com.study.review.bad.api.UserApi;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Test users and their cleanup. */
public class TestData {

    public static final String PREFIX = "autotest";

    private static final Random random = new Random();

    public static Map<String, Object> randomUser() {
        int n = random.nextInt(1000);
        Map<String, Object> user = new HashMap<>();
        user.put("name", "Auto Test " + n);
        user.put("email", PREFIX + n + "@example.com");
        user.put("age", 30);
        user.put("country", "GB");
        return user;
    }

    /** Deletes every user created by autotests. */
    public static void cleanUp() {
        UserApi api = new UserApi();
        List<Map<String, Object>> users = api.getAllUsers().jsonPath().getList("$");
        for (Map<String, Object> user : users) {
            if (((String) user.get("email")).startsWith(PREFIX)) {
                api.deleteUser(((Number) user.get("id")).longValue());
            }
        }
    }
}
