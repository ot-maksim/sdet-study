package com.study.principles.parallel.good;

import com.study.principles.parallel.Credentials;
import com.study.principles.parallel.FakeShop;
import com.study.principles.parallel.FakeShopHttp;
import com.study.principles.parallel.FakeWebDriver;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Run-wide state: created once (lazily, by the first test that needs it), shared by every test, closed
 * after the last one. Each field is shareable for a <i>stated</i> reason:
 *
 * <table>
 *   <tr><th>Field</th><th>Why it is safe to share</th></tr>
 *   <tr><td>{@code config}, {@code api}, {@code admin}</td><td>immutable</td></tr>
 *   <tr><td>{@code adminToken}</td><td>an immutable value computed once; expensive or rate-limited to get</td></tr>
 *   <tr><td>{@code names}</td><td>{@link AtomicLong}: atomic increment, plus a run id against other CI runs</td></tr>
 *   <tr><td>{@code createdUsers}</td><td>{@link ConcurrentLinkedQueue}: append-only from many tests, drained once at the end</td></tr>
 *   <tr><td>{@code premiumAccounts}</td><td>{@link AccountPool}: exclusive leases over a blocking queue</td></tr>
 * </table>
 *
 * Mutable per-test things (a browser, a test's list of leases) are only <b>produced</b> here, never held.
 */
public final class ShopEnvironment implements AutoCloseable {

    private final ShopConfig config;
    private final FakeShop shop;
    private final ShopApi api;
    private final String adminToken;
    private final ShopApi admin;
    private final String runId = UUID.randomUUID().toString().substring(0, 8);
    private final AtomicLong names = new AtomicLong();
    private final Queue<String> createdUsers = new ConcurrentLinkedQueue<>();
    private final AccountPool premiumAccounts;

    private ShopEnvironment(ShopConfig config, FakeShop shop) {
        this.config = config;
        this.shop = shop;
        this.api = new ShopApi(config, new FakeShopHttp(shop));
        this.adminToken = api.login(config.admin()); // once per run, not once per test
        this.admin = api.as(adminToken);
        this.premiumAccounts = new AccountPool(config.premiumAccounts(), config.leaseTimeout());
    }

    /** {@code new FakeShop()} stands for "connect to the stand at {@code config.baseUri()}". */
    public static ShopEnvironment connect(ShopConfig config) {
        return new ShopEnvironment(config, new FakeShop());
    }

    /** Anonymous client; derive per-user clients with {@code api().as(token)}. */
    public ShopApi api() {
        return api;
    }

    /** Admin client: immutable, so one instance serves every test. */
    public ShopApi admin() {
        return admin;
    }

    /** A new browser: the caller (the test's Store) owns it and quits it. */
    public FakeWebDriver newDriver() {
        return new FakeWebDriver(shop);
    }

    public TestUsers newTestUsers() {
        return new TestUsers(this);
    }

    Credentials createUser() {
        String username = "user-" + runId + "-" + names.incrementAndGet();
        Credentials created = admin.createUser(username);
        createdUsers.add(username);
        return created;
    }

    AccountPool premiumAccounts() {
        return premiumAccounts;
    }

    // ---- for assertions in the kata ---------------------------------------------------------------

    public int adminLogins() {
        return shop.adminLogins();
    }

    public int createdUsersToCleanUp() {
        return createdUsers.size();
    }

    public boolean userExists(String username) {
        return shop.exists(username);
    }

    /** After the last test: remove everything this run created. */
    @Override
    public void close() {
        for (String username; (username = createdUsers.poll()) != null; ) {
            admin.deleteUser(username);
        }
    }
}
