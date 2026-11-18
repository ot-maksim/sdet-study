package com.study.principles.inheritance.basetest.bad;

import com.study.principles.inheritance.basetest.ApiClient;
import com.study.principles.inheritance.basetest.Browser;
import com.study.principles.inheritance.basetest.Db;
import com.study.principles.inheritance.basetest.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.util.UUID;

/**
 * The god class every framework grows: "all tests extend BaseTest, so put it here".
 * Each line was a reasonable request once; together they are:
 * <ul>
 *   <li><b>One price for everyone</b> — an API test launches a browser and opens a DB connection it never uses.</li>
 *   <li><b>Fragile base class</b> — {@code setUp()} is {@code protected} and overridable: a subclass that writes
 *       its own {@code @BeforeEach void setUp()} <i>replaces</i> it (JUnit does not run an overridden lifecycle
 *       method), and every protected field stays {@code null}.</li>
 *   <li><b>Hidden inputs</b> — {@code browser}, {@code user}, {@code db} appear in tests from nowhere; the test's
 *       signature says nothing about what it needs.</li>
 *   <li><b>Utility drawer</b> — {@code retry}, {@code randomName}, {@code assertStored} live here only because
 *       every test can reach them; none of them needs inheritance.</li>
 * </ul>
 */
public abstract class BaseTest {

    protected ApiClient api;
    protected Db db;
    protected Browser browser;
    protected User user;

    @BeforeEach
    protected void setUp() {
        api = new ApiClient();
        db = Db.connect();
        browser = Browser.launch();
        user = api.createUser(userName());
        browser.loginAs(user);
    }

    /** Hook: override to log in as somebody else. Called from {@link #setUp()}. */
    protected String userName() {
        return "default-user";
    }

    @AfterEach
    protected void tearDown() {
        if (browser != null) browser.close();
        if (db != null) db.close();
    }

    // ---- the drawer: helpers that ended up here because "everyone extends BaseTest" ----------------

    protected void screenshot(String name) {
        browser.screenshot(name);
    }

    protected void assertStored(User expected) {
        if (db.findUser(expected.id()).filter(expected::equals).isEmpty()) {
            throw new AssertionError("not in DB: " + expected);
        }
    }

    protected String randomName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected void retry(int times, Runnable action) {
        for (int attempt = 1; ; attempt++) {
            try {
                action.run();
                return;
            } catch (RuntimeException | AssertionError e) {
                if (attempt == times) throw e;
            }
        }
    }
}
