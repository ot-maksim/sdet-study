package com.study.framework.tests;

import com.study.framework.api.ApiClient;
import com.study.framework.api.ApiException;
import com.study.framework.api.ApiSettings;
import com.study.framework.api.users.NewUsers;
import com.study.framework.api.users.UserApi;
import com.study.framework.core.config.Config;
import com.study.framework.core.data.Unique;
import com.study.framework.core.inject.Bindings;
import com.study.framework.core.inject.CompositionRoot;
import com.study.framework.core.lifecycle.Cleanup;
import com.study.framework.db.Database;
import com.study.framework.db.DbException;
import com.study.framework.db.DbSettings;
import com.study.framework.db.users.UserRows;
import com.study.framework.domain.ApiUserPrecondition;
import com.study.framework.domain.DbUserPrecondition;
import com.study.framework.domain.DomainSettings;
import com.study.framework.domain.UserChecks;
import com.study.framework.domain.UserPrecondition;
import com.study.framework.domain.UserSteps;
import com.study.framework.ui.BrowserPool;
import com.study.framework.ui.BrowserProfiles;
import com.study.framework.ui.UiSession;
import com.study.framework.ui.UiSettings;
import com.study.framework.ui.users.UsersPage;
import com.study.framework.ui.users.UsersScreen;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * <b>The</b> composition root: the only class that knows every module and says how the object graph is built.
 * Every {@code new} of the framework is in {@link #bind}; tests only declare parameters.
 *
 * <pre>
 * RUN  (one per JUnit run, shared by parallel tests; immutable or thread-safe)
 *   Config ── ApiSettings ── ApiClient ── UserApi          Unique ── NewUsers
 *          ── DbSettings  ── Database  ── UserRows ── UserChecks
 *          ── UiSettings  ── BrowserPool                   DomainSettings, Clock
 * TEST (one per test method, closed after it, also on failure)
 *   Cleanup ── UserPrecondition (api | db, by domain.user-precondition) ── UserSteps
 *           ── UiSession (lease + context, profile by ui.profile) ── UsersScreen (UsersPage)
 * </pre>
 *
 * Choices made here and nowhere else: the precondition channel, the browser profile, the screen implementation
 * (a native one would be bound here). Two more jobs that need the whole picture: skip or fail when the stand is
 * down ({@code stand.required}), and a screenshot of a failed UI test — only if the test had a browser.
 */
public final class SutComposition extends CompositionRoot implements ExecutionCondition, AfterTestExecutionCallback {

    private static volatile Optional<String> standDown;

    @Override
    protected void bind(Bindings b) {
        b.run(ApiClient.class, c -> new ApiClient(c.get(Config.class).bind(ApiSettings::from)));
        b.run(UserApi.class, c -> new UserApi(c.get(ApiClient.class)));
        b.run(NewUsers.class, c -> new NewUsers(c.get(Unique.class)));
        b.run(Database.class, c -> new Database(c.get(Config.class).bind(DbSettings::from)));
        b.run(UserRows.class, c -> new UserRows(c.get(Database.class)));
        b.run(UserChecks.class, c -> new UserChecks(c.get(UserRows.class)));
        b.run(UiSettings.class, c -> c.get(Config.class).bind(UiSettings::from));
        b.run(BrowserPool.class, c -> c.cleanup().own(new BrowserPool(c.get(UiSettings.class))));
        b.run(DomainSettings.class, c -> c.get(Config.class).bind(DomainSettings::from));
        b.run(Clock.class, c -> Clock.systemUTC());

        b.test(UserPrecondition.class, c -> switch (c.get(DomainSettings.class).userPrecondition()) {
            case "db" -> new DbUserPrecondition(c.get(UserRows.class), c.get(Clock.class), c.get(Cleanup.class));
            default -> new ApiUserPrecondition(c.get(UserApi.class), c.get(Cleanup.class));
        });
        b.test(UserSteps.class, c -> new UserSteps(c.get(UserPrecondition.class), c.get(NewUsers.class),
                c.get(UserApi.class), c.get(Cleanup.class)));
        b.test(UiSession.class, c -> {
            UiSettings ui = c.get(UiSettings.class);
            return c.cleanup().own(new UiSession(c.get(BrowserPool.class), BrowserProfiles.get(ui.profile()), ui));
        });
        b.test(UsersScreen.class, c -> new UsersPage(c.get(UiSession.class).page()));
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        Optional<String> down = standDown;
        if (down == null) {
            down = probe();
            standDown = down;
        }
        if (down.isEmpty()) {
            return ConditionEvaluationResult.enabled("stand is up");
        }
        if (Config.load().bind(r -> r.bool("stand.required"))) {
            throw new IllegalStateException(down.get() + " (stand.required=true)");
        }
        return ConditionEvaluationResult.disabled(down.get() + " (set stand.required=true to fail instead)");
    }

    @Override
    public void afterTestExecution(ExtensionContext context) {
        if (context.getExecutionException().isEmpty()) {
            return;
        }
        createdInTest(context, UiSession.class).ifPresent(session -> {
            String name = (context.getRequiredTestClass().getSimpleName() + "-" + context.getDisplayName())
                    .replaceAll("[^A-Za-z0-9._-]+", "_");
            Path file = Path.of("target", "screenshots", name + ".png");
            try {
                session.screenshot(file);
                System.err.println("Screenshot of the failed test: " + file.toAbsolutePath());
            } catch (RuntimeException e) {
                // A broken screenshot must not replace the test's own failure in the report
                System.err.println("No screenshot for " + name + ": " + e.getMessage().lines().findFirst().orElse(""));
            }
        });
    }

    private static Optional<String> probe() {
        Config config = Config.load();
        try {
            new ApiClient(config.bind(ApiSettings::from)).get("/health").expect(200);
            new Database(config.bind(DbSettings::from)).query("SELECT 1 FROM users LIMIT 0", rs -> 1);
            return Optional.empty();
        } catch (ApiException | DbException e) {
            return Optional.of("Stand is down (mvn -q -pl sut -am package -DskipTests && docker compose up -d --build --wait): "
                    + e.getMessage().lines().findFirst().orElse(""));
        }
    }
}
