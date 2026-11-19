package com.study.template;

import com.study.framework.core.config.Secret;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Every key of the template, bound by Spring Boot from {@code application.yml}, {@code application-<profile>.yml},
 * system properties and environment variables ({@code TEMPLATE_UI_PROFILE=mobile}).
 *
 * <p>{@code ignoreUnknownFields = false} brings back what stage 2's catalogue did: a typo under {@code template.*}
 * ({@code template.ui.max-browser}) fails the context instead of being silently ignored — Spring's default.
 * Values are not checked here twice: the framework's settings records ({@code UiSettings}, {@code DbSettings})
 * still validate what they get.
 */
@ConfigurationProperties(prefix = "template", ignoreUnknownFields = false)
public record TemplateProperties(EnvironmentKind environment, boolean requireEnvironment,
                                 Stand stand, Containers containers, Api api, Ui ui, Domain domain, Events events) {

    public TemplateProperties {
        Objects.requireNonNull(environment, "template.environment is missing");
        Objects.requireNonNull(stand, "template.stand is missing");
        Objects.requireNonNull(containers, "template.containers is missing");
        Objects.requireNonNull(api, "template.api is missing");
        Objects.requireNonNull(ui, "template.ui is missing");
        Objects.requireNonNull(domain, "template.domain is missing");
        Objects.requireNonNull(events, "template.events is missing");
    }

    /** Where the SUT comes from. An enum, not a free string: a typo fails binding with the allowed values. */
    public enum EnvironmentKind {
        /** Already running (compose, a shared QA stand): the template only connects. */
        STAND,
        /** Started by the run itself with Testcontainers: Postgres + the SUT image; hermetic. */
        CONTAINERS
    }

    /**
     * A running stand. The password is a {@link Secret} already at binding: it never sits in a String field of a
     * record that may be logged. {@code kafkaBootstrap} empty: the stand has no broker, event tests are skipped.
     */
    public record Stand(URI sutUrl, String dbUrl, String dbUser, Secret dbPassword, String kafkaBootstrap) {
    }

    /** {@code sutBuildDir}: the SUT's Dockerfile and {@code target/sut.jar}; in real life, an image from a registry instead. */
    public record Containers(String postgresImage, String kafkaImage, Path sutBuildDir) {
    }

    public record Api(Duration timeout) {
    }

    public record Ui(String profile, boolean headless, Duration timeout, int maxBrowsers) {
    }

    /** {@code timeout}: how long a test waits for an event it expects; the upper bound, not a pause. */
    public record Events(Duration timeout) {
    }

    public record Domain(Channel userPrecondition) {

        public enum Channel {
            API, DB
        }
    }
}
