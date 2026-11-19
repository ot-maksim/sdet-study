package com.study.framework.db;

import com.study.framework.core.config.Config;
import java.util.Optional;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Runs a test class only when the stand's Postgres is reachable and has the SUT's schema; otherwise it is
 * skipped with the reason, not failed: {@code mvn verify} must pass without Docker. Testcontainers would make
 * these tests hermetic — stage 4 (docs/PLAN.md).
 */
public final class StandDatabase implements ExecutionCondition {

    private static volatile Optional<String> unavailable;

    public static Database database() {
        return new Database(Config.load().bind(DbSettings::from));
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        Optional<String> reason = unavailable;
        if (reason == null) {
            reason = probe();
            unavailable = reason;
        }
        return reason.map(ConditionEvaluationResult::disabled)
                .orElseGet(() -> ConditionEvaluationResult.enabled("stand database is up"));
    }

    private static Optional<String> probe() {
        try {
            database().query("SELECT 1 FROM users LIMIT 0", rs -> 1);
            return Optional.empty();
        } catch (DbException e) {
            return Optional.of("No stand database (start it: docker compose up -d --wait): " + e.getMessage());
        }
    }
}
