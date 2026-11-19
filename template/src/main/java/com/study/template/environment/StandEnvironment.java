package com.study.template.environment;

import com.study.framework.api.ApiClient;
import com.study.framework.api.ApiException;
import com.study.framework.api.ApiSettings;
import com.study.framework.db.Database;
import com.study.framework.db.DbException;
import com.study.framework.db.DbSettings;
import com.study.template.TemplateProperties;
import java.time.Duration;
import java.util.Optional;

/**
 * A stand that is already running (the compose stand, a shared QA environment): nothing to start, only to check.
 * The same behaviour as stage 2's {@code SutComposition}, now one implementation among others.
 */
public final class StandEnvironment implements EnvironmentProvider {

    private final TemplateProperties.Stand stand;
    private final Duration timeout;
    private volatile Optional<String> unavailable;

    public StandEnvironment(TemplateProperties.Stand stand, Duration timeout) {
        this.stand = stand;
        this.timeout = timeout;
    }

    @Override
    public Optional<String> unavailable() {
        Optional<String> result = unavailable;
        if (result == null) {
            result = probe();
            unavailable = result;
        }
        return result;
    }

    @Override
    public SutEnvironment start() {
        return new SutEnvironment("stand", stand.sutUrl(), stand.dbUrl(), stand.dbUser(), stand.dbPassword());
    }

    private Optional<String> probe() {
        try {
            new ApiClient(new ApiSettings(stand.sutUrl(), timeout)).get("/health").expect(200);
            new Database(new DbSettings(stand.dbUrl(), stand.dbUser(), stand.dbPassword()))
                    .query("SELECT 1 FROM users LIMIT 0", rs -> 1);
            return Optional.empty();
        } catch (ApiException | DbException e) {
            return Optional.of("Stand is down (mvn -q -pl sut -am package -DskipTests && docker compose up -d --build --wait,"
                    + " or drop the stand profile to start one with Testcontainers): "
                    + e.getMessage().lines().findFirst().orElse(""));
        }
    }

    @Override
    public String toString() {
        return "StandEnvironment(" + stand.sutUrl() + ")";
    }
}
