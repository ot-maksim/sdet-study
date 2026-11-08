package com.study.principles.encapsulation.bad;

import com.study.principles.encapsulation.FakeTransport;

/**
 * BAD: a test that needs another host and simply mutates the global config.
 * It "forgets" to restore it — and even a restore in {@code @AfterEach} would not help
 * tests running in parallel at the same moment.
 */
public class AdminSmokeTest {
    private final FakeTransport transport;

    public AdminSmokeTest(FakeTransport transport) {
        this.transport = transport;
    }

    public void adminCanSeeUser() {
        TestConfig.get().setBaseUrl("http://admin.local:9090");
        new UsersApiClient(transport).getUser(1);
    }
}
