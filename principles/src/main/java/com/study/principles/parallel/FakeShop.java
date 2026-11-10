package com.study.principles.parallel;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Stand-in for the deployed system under test: accounts, a small API and a profile page.
 * {@code new FakeShop()} plays "connect to the stand from config": every connection gets a clean shop.
 *
 * <p>Like a real server it is safe for concurrent use (concurrent maps, atomics). The stand is shared by
 * every test by nature, so tests isolate themselves by <b>data</b> (their own users), not by instances.
 *
 * <p>Rules that matter for the kata:
 * <ul>
 *   <li>Only an admin may create users.</li>
 *   <li>Premium accounts are pre-provisioned (paid plans cannot be created through the API) and allow
 *       <b>one active session</b>: a new login ends the previous one (anti account-sharing, like
 *       streaming services). Two tests sharing such an account kick each other out.</li>
 *   <li>The API greets in the language of the <b>first</b> {@code Accept-Language} value, like most servers.</li>
 *   <li>Feature flags (admin only) are <b>global</b> by default: switching {@code new-profile} on changes the
 *       profile page for every user, and so for every test running at that moment. A per-user override
 *       wins over the global value (flag targeting, as in LaunchDarkly/Unleash).</li>
 * </ul>
 */
public final class FakeShop {

    public static final Credentials ADMIN = new Credentials("admin", "admin-pass");
    public static final List<Credentials> PREMIUM_ACCOUNTS = List.of(
            new Credentials("premium-1", "p1-pass"),
            new Credentials("premium-2", "p2-pass"));

    public enum Plan { FREE, PREMIUM }

    public record Account(String username, String password, Plan plan, boolean admin) {
    }

    /** What the browser shows: the final URL after redirects and element texts by id. */
    public record Page(String url, Map<String, String> elements) {
        public static final Page BLANK = new Page("about:blank", Map.of());
    }

    /** An HTTP-level error: the transport layer turns it into a status code. */
    public static final class ShopError extends RuntimeException {
        private final int status;

        ShopError(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    private final ConcurrentMap<String, Account> accounts = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> sessions = new ConcurrentHashMap<>();        // token -> username
    private final ConcurrentMap<String, String> premiumSessions = new ConcurrentHashMap<>(); // username -> the only valid token
    private final AtomicLong tokenSequence = new AtomicLong();
    private final AtomicInteger adminLogins = new AtomicInteger();
    private final ConcurrentMap<String, Boolean> globalFlags = new ConcurrentHashMap<>();  // flag -> on
    private final ConcurrentMap<String, Boolean> userFlags = new ConcurrentHashMap<>();    // "flag/username" -> on

    public FakeShop() {
        accounts.put(ADMIN.username(), new Account(ADMIN.username(), ADMIN.password(), Plan.FREE, true));
        PREMIUM_ACCOUNTS.forEach(c -> accounts.put(c.username(), new Account(c.username(), c.password(), Plan.PREMIUM, false)));
    }

    // ---- API ---------------------------------------------------------------------------------------

    public String login(String username, String password) {
        Account account = accounts.get(username);
        if (account == null || !account.password().equals(password)) {
            throw new ShopError(401, "bad credentials for " + username);
        }
        if (account.admin()) {
            adminLogins.incrementAndGet();
        }
        String token = username + "-token-" + tokenSequence.incrementAndGet();
        sessions.put(token, username);
        if (account.plan() == Plan.PREMIUM) {
            premiumSessions.put(username, token); // atomically ends the previous session
        }
        return token;
    }

    public Account whoIs(String token) {
        return session(token).orElseThrow(() -> new ShopError(401, "no valid session"));
    }

    public Credentials createUser(String token, String username) {
        requireAdmin(token);
        String password = username + "-pass";
        Account previous = accounts.putIfAbsent(username, new Account(username, password, Plan.FREE, false));
        if (previous != null) {
            throw new ShopError(409, "user already exists: " + username);
        }
        return new Credentials(username, password);
    }

    public void deleteUser(String token, String username) {
        requireAdmin(token);
        if (accounts.remove(username) == null) {
            throw new ShopError(404, "no such user: " + username);
        }
    }

    /** {@code username == null}: the global value for everybody; otherwise an override for one user. */
    public void setFeature(String token, String flag, String username, boolean enabled) {
        requireAdmin(token);
        if (username == null) {
            globalFlags.put(flag, enabled);
        } else {
            userFlags.put(flag + "/" + username, enabled);
        }
    }

    public boolean isEnabled(String flag, String username) {
        Boolean override = userFlags.get(flag + "/" + username);
        return override != null ? override : globalFlags.getOrDefault(flag, false);
    }

    public String greeting(Account account, String language) {
        return ("de".equals(language) ? "Hallo, " : "Hello, ") + account.username();
    }

    // ---- UI ----------------------------------------------------------------------------------------

    public Page render(String path, String sessionToken) {
        if (!"/profile".equals(path)) {
            return new Page(path, Map.of("title", "Not found"));
        }
        return session(sessionToken)
                .map(a -> new Page("/profile", Map.of(
                        "title", isEnabled("new-profile", a.username()) ? "Your profile" : "Profile",
                        "greeting", greeting(a, "en"),
                        "plan", a.plan().name())))
                .orElseGet(() -> new Page("/login", Map.of("title", "Log in")));
    }

    // ---- for assertions ----------------------------------------------------------------------------

    public int adminLogins() {
        return adminLogins.get();
    }

    /** Users created through the API (seeded accounts excluded). */
    public int createdUsers() {
        return accounts.size() - 1 - PREMIUM_ACCOUNTS.size();
    }

    public boolean exists(String username) {
        return accounts.containsKey(username);
    }

    private Optional<Account> session(String token) {
        if (token == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(sessions.get(token))
                .map(accounts::get)
                .filter(a -> a.plan() != Plan.PREMIUM || token.equals(premiumSessions.get(a.username())));
    }

    private void requireAdmin(String token) {
        if (!whoIs(token).admin()) {
            throw new ShopError(403, "admin only");
        }
    }
}
