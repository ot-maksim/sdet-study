package com.study.framework.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfigTest {

    private static final ClassLoader CLASS_PATH = ConfigTest.class.getClassLoader();

    /** What a module would bind its keys into. */
    record SutSettings(URI baseUrl, Duration timeout, String dbUser, Secret dbPassword) {
        SutSettings {
            if (timeout.isNegative()) {
                throw new IllegalArgumentException("timeout must not be negative");
            }
        }

        static SutSettings from(ConfigReader r) {
            return new SutSettings(r.uri("sut.base-url"), r.duration("api.timeout"), r.string("db.user"), r.secret("db.password"));
        }
    }

    private static Config load(Map<String, String> systemProperties, Map<String, String> environment) {
        return Config.load(systemProperties, environment, CLASS_PATH);
    }

    @Test
    void layersOverrideInOrderSystemPropertyEnvVariableEnvFileDefaults() {
        Config defaultsOnly = load(Map.of(), Map.of());
        Config fromEnvVar = load(Map.of(), Map.of("SUT_BASE_URL", "http://from-env:1"));
        Config fromBoth = load(Map.of("sut.base-url", "http://from-property:2"), Map.of("SUT_BASE_URL", "http://from-env:1"));

        assertThat(defaultsOnly.bind(SutSettings::from).baseUrl()).isEqualTo(URI.create("http://localhost:7070"));
        assertThat(defaultsOnly.bind(SutSettings::from).dbPassword().reveal()).isEqualTo("local-secret");
        assertThat(fromEnvVar.bind(SutSettings::from).baseUrl()).isEqualTo(URI.create("http://from-env:1"));
        assertThat(fromBoth.bind(SutSettings::from).baseUrl()).isEqualTo(URI.create("http://from-property:2"));
    }

    @Test
    void envSelectsTheStandFile() {
        Config qa = load(Map.of(), Map.of("FRAMEWORK_ENV", "qa"));

        assertThat(qa.env()).isEqualTo("qa");
        assertThat(qa.bind(SutSettings::from).baseUrl()).isEqualTo(URI.create("https://qa.example.test"));
        assertThat(qa.bind(SutSettings::from).dbPassword().reveal()).isEqualTo("qa-secret");
    }

    @Test
    void unknownEnvFailsFast() {
        assertThatThrownBy(() -> load(Map.of("framework.env", "prod"), Map.of()))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("env=prod")
                .hasMessageContaining("no env/prod.properties on the class path");
    }

    @Test
    void aTypoInAnEnvFileIsAnUnknownKey() {
        assertThatThrownBy(() -> load(Map.of("framework.env", "typo"), Map.of()))
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("unknown key sut.base-ulr in env/typo.properties")
                .hasMessageContaining("sut.base-url");
    }

    @Test
    void bindReportsEveryProblemAtOnceWithWhereItLookedAndWhereAValueCameFrom() {
        Config broken = load(Map.of("api.timeout", "five seconds"), Map.of("SUT_BASE_URL", "localhost:7070/no-scheme", "DB_PASSWORD", " "));

        assertThatThrownBy(() -> broken.bind(SutSettings::from))
                .isInstanceOfSatisfying(ConfigException.class, e -> assertThat(e.problems()).containsExactly(
                        "sut.base-url: 'localhost:7070/no-scheme' is not an absolute URI with a host (from environment variables)",
                        "api.timeout: 'five seconds' is not a duration like 500ms, 5s, 2m or PT5S (from system properties)",
                        "missing db.password (looked in: system properties (db.password), environment variables (DB_PASSWORD),"
                                + " env/local.properties (db.password), framework.properties (db.password))"));
    }

    @Test
    void aBinderFailingOnAPlaceholderStillReportsTheConfigProblem() {
        Config noTimeout = Config.of("test", List.of(ConfigSource.file("defaults", Map.of(
                "sut.base-url", "http://x:1", "api.timeout", "", "db.user", "u", "db.password", "p"))));

        assertThatThrownBy(() -> noTimeout.bind(SutSettings::from))   // the record's constructor NPEs on null timeout
                .isInstanceOf(ConfigException.class)
                .hasMessageContaining("missing api.timeout");
    }

    @Test
    void durationsReadShortAndIsoForms() {
        Config config = Config.of("test", List.of(ConfigSource.file("defaults",
                Map.of("a", "500ms", "b", "5s", "c", "2m", "d", "1h", "e", "PT1M30S"))));

        List<Duration> durations = config.bind(r -> List.of(r.duration("a"), r.duration("b"), r.duration("c"), r.duration("d"), r.duration("e")));

        assertThat(durations).containsExactly(Duration.ofMillis(500), Duration.ofSeconds(5), Duration.ofMinutes(2), Duration.ofHours(1), Duration.ofSeconds(90));
    }

    @Test
    void describeShowsEveryKeyWithItsOriginAndMasksSecrets() {
        String described = load(Map.of(), Map.of("API_TIMEOUT", "10s")).describe();

        assertThat(described)
                .contains("Configuration (env=local):")
                .contains("api.timeout = 10s   [environment variables]")
                .contains("sut.base-url = http://localhost:7070   [framework.properties]")
                .contains("db.password = ****   [env/local.properties]")
                .doesNotContain("local-secret");
        assertThat(load(Map.of(), Map.of()).bind(SutSettings::from).toString()).contains("dbPassword=****").doesNotContain("local-secret");
    }
}
