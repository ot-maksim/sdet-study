package com.study.framework.api;

import com.study.sut.SutApp;
import com.study.sut.users.InMemoryUserRepository;
import com.study.sut.users.UserService;
import io.javalin.Javalin;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;

/**
 * The real SUT on a free port with in-memory storage: the client is checked against the actual HTTP
 * contract — statuses, headers, JSON — without Docker and without a hand-written HTTP mock that could
 * drift from the service.
 */
public final class InProcessSut implements AutoCloseable {

    private final Javalin app = SutApp.create(new UserService(new InMemoryUserRepository(), Clock.systemUTC())).start(0);

    public ApiSettings settings() {
        return new ApiSettings(URI.create("http://localhost:" + app.port()), Duration.ofSeconds(5));
    }

    @Override
    public void close() {
        app.stop();
    }
}
