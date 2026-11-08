package com.study.principles.encapsulation.good;

import com.study.principles.encapsulation.FakeTransport;

/**
 * GOOD: needs another host → builds its own modified copy. The shared config cannot be touched,
 * so there is nothing to restore and nothing for parallel tests to trip over.
 */
public class AdminSmokeTest {
    private final TestConfig config;
    private final FakeTransport transport;

    public AdminSmokeTest(TestConfig config, FakeTransport transport) {
        this.config = config;
        this.transport = transport;
    }

    public void adminCanSeeUser() {
        TestConfig admin = config.withBaseUrl("http://admin.local:9090");
        new UsersApiClient(admin, transport).getUser(1);
    }
}
