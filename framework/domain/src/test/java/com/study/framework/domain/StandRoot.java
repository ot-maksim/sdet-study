package com.study.framework.domain;

import com.study.framework.api.ApiClient;
import com.study.framework.api.ApiException;
import com.study.framework.api.ApiSettings;
import com.study.framework.api.users.NewUsers;
import com.study.framework.api.users.UserApi;
import com.study.framework.core.config.Config;
import com.study.framework.core.data.Unique;
import com.study.framework.core.inject.Bindings;
import com.study.framework.core.inject.CompositionRoot;
import com.study.framework.db.Database;
import com.study.framework.db.DbException;
import com.study.framework.db.DbSettings;
import com.study.framework.db.users.UserRows;
import com.study.framework.ui.BrowserPool;
import com.study.framework.ui.UiSettings;
import java.util.Optional;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Composition root of the domain module's tests, against the compose stand; skips the class with the reason when
 * the stand is down. A preview of {@code framework/tests}' root, without the channel choice.
 */
public final class StandRoot extends CompositionRoot implements ExecutionCondition {

    private static volatile Optional<String> unavailable;

    @Override
    protected void bind(Bindings b) {
        b.run(ApiClient.class, c -> new ApiClient(c.get(Config.class).bind(ApiSettings::from)));
        b.run(UserApi.class, c -> new UserApi(c.get(ApiClient.class)));
        b.run(NewUsers.class, c -> new NewUsers(c.get(Unique.class)));
        b.run(UserRows.class, c -> new UserRows(new Database(c.get(Config.class).bind(DbSettings::from))));
        b.run(UserChecks.class, c -> new UserChecks(c.get(UserRows.class)));
        b.run(UiSettings.class, c -> c.get(Config.class).bind(UiSettings::from));
        b.run(BrowserPool.class, c -> c.cleanup().own(new BrowserPool(c.get(UiSettings.class))));
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        Optional<String> reason = unavailable;
        if (reason == null) {
            reason = probe();
            unavailable = reason;
        }
        return reason.map(ConditionEvaluationResult::disabled).orElseGet(() -> ConditionEvaluationResult.enabled("stand is up"));
    }

    private static Optional<String> probe() {
        Config config = Config.load();
        try {
            new ApiClient(config.bind(ApiSettings::from)).get("/health").expect(200);
            new Database(config.bind(DbSettings::from)).query("SELECT 1 FROM users LIMIT 0", rs -> 1);
            return Optional.empty();
        } catch (ApiException | DbException e) {
            return Optional.of("Stand is down (docker compose up -d --wait): " + e.getMessage());
        }
    }
}
