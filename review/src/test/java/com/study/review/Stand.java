package com.study.review;

import com.study.review.bad.config.FrameworkConfig;
import com.study.sut.SutApp;
import com.study.sut.users.InMemoryUserRepository;
import com.study.sut.users.UserService;
import io.javalin.Javalin;
import io.restassured.RestAssured;
import java.time.Clock;

/**
 * The real SUT on a free port with in-memory storage. {@link #shared()} is the stand both frameworks test in
 * this module; {@link #start()} gives a second one when a test needs two.
 */
public final class Stand implements AutoCloseable {

    /** The bad framework's database: deliberately unreachable, so tests show who depends on it. */
    public static final String UNREACHABLE_DB = "jdbc:postgresql://localhost:1/sut";

    private static Stand shared;

    private final Javalin app;

    private Stand() {
        app = SutApp.create(new UserService(new InMemoryUserRepository(), Clock.systemUTC())).start(0);
    }

    public static Stand start() {
        return new Stand();
    }

    public static synchronized Stand shared() {
        if (shared == null) {
            shared = new Stand();
            Runtime.getRuntime().addShutdownHook(new Thread(shared::close));
        }
        return shared;
    }

    /**
     * Points the bad framework at the shared stand. Its config is a singleton that reads system properties once,
     * so this must run before anything touches it; RestAssured's static base URI is reset on every call because
     * other tests change it.
     */
    public static synchronized void forBadFramework() {
        System.setProperty("base.url", shared().url());
        System.setProperty("db.url", UNREACHABLE_DB);
        String seen = FrameworkConfig.getInstance().get("base.url");
        if (!shared().url().equals(seen)) {
            throw new IllegalStateException("FrameworkConfig was created before the stand was set: " + seen);
        }
        RestAssured.baseURI = shared().url();
    }

    public String url() {
        return "http://localhost:" + app.port();
    }

    @Override
    public void close() {
        app.stop();
    }
}
