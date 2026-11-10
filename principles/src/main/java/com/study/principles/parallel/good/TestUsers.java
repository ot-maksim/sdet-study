package com.study.principles.parallel.good;

import com.study.principles.parallel.Credentials;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-test entry point to test accounts. One instance per test, so its own list of leases needs no
 * synchronization (thread confinement); the shared parts it talks to are the thread-safe
 * {@link ShopEnvironment}.
 *
 * <p>{@link #close()} returns leased accounts even if the test failed: the JUnit {@code Store} closes it
 * after the test.
 */
public final class TestUsers implements AutoCloseable {

    private final ShopEnvironment environment;
    private final List<Credentials> leased = new ArrayList<>();

    TestUsers(ShopEnvironment environment) {
        this.environment = environment;
    }

    /** A brand-new user only this test knows about: isolation by data. */
    public Credentials create() {
        return environment.createUser();
    }

    /** One of the pre-provisioned premium accounts, exclusively for this test. Blocks while all are busy. */
    public Credentials leasePremium() {
        Credentials account = environment.premiumAccounts().lease();
        leased.add(account);
        return account;
    }

    @Override
    public void close() {
        leased.forEach(environment.premiumAccounts()::release);
        leased.clear();
    }
}
