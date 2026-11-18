package com.study.principles.inheritance.basetest;

/** Fake REST client: cheap, but still a resource the test pays for when it is created. */
public final class ApiClient {

    public ApiClient() {
        Startups.record("api client");
    }

    public User createUser(String name) {
        User user = new User(Stand.IDS.incrementAndGet(), name);
        Stand.USERS.put(user.id(), user);
        return user;
    }
}
