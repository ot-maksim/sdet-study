package com.study.principles.encapsulation.bad;

import com.study.principles.encapsulation.FakeTransport;

import java.time.Duration;

/**
 * BAD: another client, another author, opposite assumptions about the same two values.
 * Its author assumed: baseUrl HAS a trailing slash, timeout is in seconds.
 * No value of {@link TestConfig} can satisfy both this class and {@link UsersApiClient}.
 */
public class LoginPage {
    private final FakeTransport transport;

    public LoginPage(FakeTransport transport) {
        this.transport = transport;
    }

    public void open() {
        TestConfig cfg = TestConfig.get();
        transport.send("OPEN",
                cfg.getBaseUrl() + "login",                     // assumption #1: trailing slash
                Duration.ofSeconds(cfg.getTimeout()));          // assumption #2: seconds
    }
}
