package com.study.principles.encapsulation.good;

import com.study.principles.encapsulation.FakeTransport;

/** GOOD: a different author can no longer make different assumptions — there is nothing left to assume. */
public class LoginPage {
    private final TestConfig config;
    private final FakeTransport transport;

    public LoginPage(TestConfig config, FakeTransport transport) {
        this.config = config;
        this.transport = transport;
    }

    public void open() {
        transport.send("OPEN", config.endpoint("login").toString(), config.timeout());
    }
}
