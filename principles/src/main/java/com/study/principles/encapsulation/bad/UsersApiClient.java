package com.study.principles.encapsulation.bad;

import com.study.principles.encapsulation.FakeTransport;

import java.time.Duration;

/**
 * BAD: the client knows HOW the config is represented.
 * Its author assumed: baseUrl has no trailing slash, timeout is in milliseconds.
 */
public class UsersApiClient {
    private final FakeTransport transport;

    public UsersApiClient(FakeTransport transport) {
        this.transport = transport;
    }

    public void getUser(long id) {
        TestConfig cfg = TestConfig.get();
        transport.send("GET",
                cfg.getBaseUrl() + "/api/users/" + id,          // assumption #1: no trailing slash
                Duration.ofMillis(cfg.getTimeout()));           // assumption #2: millis
    }
}
