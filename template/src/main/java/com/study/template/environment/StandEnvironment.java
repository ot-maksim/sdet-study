package com.study.template.environment;

import com.study.framework.api.ApiClient;
import com.study.framework.api.ApiException;
import com.study.framework.api.ApiSettings;
import com.study.framework.db.Database;
import com.study.framework.db.DbException;
import com.study.framework.db.DbSettings;
import com.study.template.TemplateProperties;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;

/**
 * A stand that is already running (the compose stand, a shared QA environment): nothing to start, only to check.
 * The same behaviour as stage 2's {@code SutComposition}, now one implementation among others.
 */
public final class StandEnvironment implements EnvironmentProvider {

    private final TemplateProperties.Stand stand;
    private final Duration timeout;
    private volatile Optional<String> unavailable;
    private volatile Optional<String> eventsUnavailable;

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
            return Optional.of("The stand has no Kafka (template.stand.kafka-bootstrap is empty): start compose with"
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
