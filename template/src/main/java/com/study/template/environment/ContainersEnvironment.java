package com.study.template.environment;

import com.study.framework.core.config.Secret;
import com.study.template.TemplateProperties;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

/**
 * A hermetic environment owned by the run: Postgres and the SUT image on a private Docker network, random host
 * ports, a fresh database. Needs only Docker — no stand, no fixed ports, two runs side by side do not collide.
 *
 * <pre>
 *   host (tests) ──localhost:random──▶ sut:7070 ──postgres:5432──▶ postgres     (private network)
 *        └────────────localhost:random──────────────────────────────▶ postgres  (framework/db reads it directly)
 * </pre>
 *
 * Started once per Spring context (the context is cached for the whole run), stopped when the context closes;
 * Testcontainers' Ryuk removes the containers even if the JVM is killed.
 */
public final class ContainersEnvironment implements EnvironmentProvider, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ContainersEnvironment.class);
    private static final int SUT_PORT = 7070;

    private final TemplateProperties.Containers settings;
    private volatile Optional<String> unavailable;

    private Network network;
    private PostgreSQLContainer<?> postgres;
    private GenericContainer<?> sut;
    private SutEnvironment started;

    public ContainersEnvironment(TemplateProperties.Containers settings) {
        this.settings = settings;
    }

    @Override
    public Optional<String> unavailable() {
        Optional<String> result = unavailable;
        if (result == null) {
            result = check();
            unavailable = result;
        }
        return result;
    }

    @Override
    public synchronized SutEnvironment start() {
        if (started != null) {
            return started;
        }
        long begin = System.nanoTime();
        network = Network.newNetwork();
        // The password is generated: the provider owns the credentials, nobody has to configure or leak them
        String password = UUID.randomUUID().toString();
        postgres = new PostgreSQLContainer<>(DockerImageName.parse(settings.postgresImage()).asCompatibleSubstituteFor("postgres"))
                .withNetwork(network)
                .withNetworkAliases("postgres")
                .withDatabaseName("sut")
                .withUsername("sut")
                .withPassword(password);
        postgres.start();

        Path dir = settings.sutBuildDir();
        // Only the two files the Dockerfile needs go to the build context, not the whole module with target/
        sut = new GenericContainer<>(new ImageFromDockerfile("sdet-sut-template", false)
                .withFileFromPath("Dockerfile", dir.resolve("Dockerfile"))
                .withFileFromPath("target/sut.jar", jar()))
                .withNetwork(network)
                .withEnv(Map.of(
                        "SUT_PORT", String.valueOf(SUT_PORT),
                        "SUT_STORAGE", "postgres",
                        "SUT_DB_URL", "jdbc:postgresql://postgres:5432/sut",   // the alias: inside the network
                        "SUT_DB_USER", "sut",
                        "SUT_DB_PASSWORD", password))
                .withExposedPorts(SUT_PORT)
                .waitingFor(Wait.forHttp("/health").forStatusCode(200).withStartupTimeout(Duration.ofSeconds(60)));
        sut.start();

        started = new SutEnvironment("containers",
                URI.create("http://" + sut.getHost() + ":" + sut.getMappedPort(SUT_PORT)),
                postgres.getJdbcUrl(), postgres.getUsername(), Secret.of(password));
        LOG.info("Environment started in {} ms: {}", (System.nanoTime() - begin) / 1_000_000, started);
        return started;
    }

    /** Called by Spring when the context closes (an inferred destroy method of the bean). */
    @Override
    public synchronized void close() {
        // Reverse order of start; each one even if the previous failed
        for (AutoCloseable resource : new AutoCloseable[] {sut, postgres, network}) {
            if (resource != null) {
                try {
                    resource.close();
                } catch (Exception e) {
                    LOG.warn("Could not stop {}: {}", resource, e.toString());
                }
            }
        }
    }

    private Optional<String> check() {
        if (!DockerClientFactory.instance().isDockerAvailable()) {
            return Optional.of("Docker is not available, so Testcontainers cannot start the environment;"
                    + " start Docker or run against a stand: -Dspring.profiles.active=stand");
        }
        if (!Files.isRegularFile(jar())) {
            return Optional.of("No " + jar().toAbsolutePath().normalize()
                    + " to build the SUT image from: mvn -q -pl sut -am package -DskipTests");
        }
        return Optional.empty();
    }

    private Path jar() {
        return settings.sutBuildDir().resolve("target").resolve("sut.jar");
    }

    @Override
    public String toString() {
        return "ContainersEnvironment(" + settings.postgresImage() + ", " + settings.sutBuildDir() + ")";
    }
}
