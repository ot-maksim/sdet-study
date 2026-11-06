package com.study.principles.dip.good;

import com.study.principles.dip.User;

/** Detail #2: creates the user via REST (in real life: RestAssured given().body(..).post("/users")). */
public class ApiUserPrecondition implements UserPrecondition {
    private final String baseUrl;
    private long seq = 200;

    public ApiUserPrecondition(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @Override
    public User existingUser(String name) {
        System.out.println("  [HTTP] POST " + baseUrl + "/users {\"name\":\"" + name + "\"}");
        return new User(seq++, name);
    }
}
