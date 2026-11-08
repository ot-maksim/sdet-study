package com.study.principles.encapsulation.good;

import com.study.principles.encapsulation.FakeTransport;

/** GOOD: says WHAT it needs ("the users endpoint", "the timeout") and knows nothing about the representation. */
public class UsersApiClient {
    private final TestConfig config;
    private final FakeTransport transport;

    public UsersApiClient(TestConfig config, FakeTransport transport) {
        this.config = config;
        this.transport = transport;
    }

    public void getUser(long id) {
        transport.send("GET", config.endpoint("/api/users/" + id).toString(), config.timeout());
    }
}
