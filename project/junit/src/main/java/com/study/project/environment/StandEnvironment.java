package com.study.project.environment;

import com.study.project.api.ApiException;
import com.study.project.api.ApiSettings;
import com.study.project.api.ApiTransport;
import com.study.project.api.SutHealth;
import com.study.project.db.Database;
import com.study.project.db.DbException;
import com.study.project.db.DbSettings;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;

/**
 * A stand that is already running (the compose stand, a shared QA environment): nothing to start, only to check.
 * One implementation of two; the other starts its own ({@link ContainersEnvironment}).
 */
public final class StandEnvironment implements EnvironmentProvider {

    private final StandSettings stand;
    private final Duration timeout;
    private volatile Optional<String> unavailable;
    private volatile Optional<String> eventsUnavailable;

    public StandEnvironment(StandSettings stand, Duration timeout) {
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
        return new SutEnvironment("stand", stand.sutUrl(), stand.dbUrl(), stand.dbUser(), stand.dbPassword(), kafka());
    }

    @Override
    public Optional<String> eventsUnavailable() {
        Optional<String> result = eventsUnavailable;
        if (result == null) {
            result = probeKafka();
            eventsUnavailable = result;
        }
        return result;
    }

    private Optional<String> kafka() {
        return Optional.ofNullable(stand.kafkaBootstrap()).filter(k -> !k.isBlank());
    }

    private Optional<String> probeKafka() {
        Optional<String> bootstrap = kafka();
        if (bootstrap.isEmpty()) {
            return Optional.of("The stand has no Kafka (project.stand.kafka-bootstrap is empty): start compose with"
                    + " --profile events and set it to localhost:9094, or drop the stand profile");
        }
        int timeoutMs = Math.toIntExact(timeout.toMillis());
        try (Admin admin = Admin.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap.get(),
                AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, timeoutMs,
                AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, timeoutMs))) {
            admin.describeCluster().clusterId().get(timeoutMs, TimeUnit.MILLISECONDS);
            return Optional.empty();
        } catch (ExecutionException | TimeoutException e) {
            return Optional.of("Kafka of the stand does not answer at " + bootstrap.get() + " within " + timeout
                    + " (docker compose --profile events up -d --wait): " + e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.of("Interrupted while probing Kafka at " + bootstrap.get());
        }
    }

    private Optional<String> probe() {
        try {
            new SutHealth(new ApiTransport(new ApiSettings(stand.sutUrl(), timeout), List.of())).expectUp();
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
