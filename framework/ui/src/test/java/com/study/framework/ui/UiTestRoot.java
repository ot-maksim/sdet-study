package com.study.framework.ui;

import com.study.framework.core.inject.Bindings;
import com.study.framework.core.inject.CompositionRoot;
import com.study.sut.SutApp;
import com.study.sut.users.InMemoryUserRepository;
import com.study.sut.users.UserService;
import io.javalin.Javalin;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;

/**
 * Composition root of the ui module's own tests: the real SUT in-process (in-memory storage, free port) and a
 * browser pool, both per run. Also a dress rehearsal of the framework's root in {@code framework/tests}.
 */
public final class UiTestRoot extends CompositionRoot {

    /** The SUT for one run. */
    public static final class InProcessSut implements AutoCloseable {
        private final Javalin app = SutApp.create(new UserService(new InMemoryUserRepository(), Clock.systemUTC())).start(0);

        public URI baseUrl() {
            return URI.create("http://localhost:" + app.port());
        }

        @Override
        public void close() {
            app.stop();
        }
    }

    @Override
    protected void bind(Bindings b) {
        b.run(InProcessSut.class, c -> c.cleanup().own(new InProcessSut()));
        b.run(UiSettings.class, c -> new UiSettings(c.get(InProcessSut.class).baseUrl(), "desktop", true, Duration.ofSeconds(5), 4));
        b.run(BrowserPool.class, c -> c.cleanup().own(new BrowserPool(c.get(UiSettings.class))));
    }
}
