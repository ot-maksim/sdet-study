package com.study.project.assembly;

import com.study.project.environment.ContainersSettings;
import com.study.project.environment.StandSettings;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Every key of the project, bound by Spring Boot from {@code application.yml}, {@code application-<profile>.yml},
 * system properties and environment variables ({@code PROJECT_UI_PROFILE=mobile}).
 *
 * <p>{@code ignoreUnknownFields = false}: a typo under {@code project.*} ({@code project.ui.max-browser}) fails the
 * context instead of being silently ignored — Spring's default. Values are not checked here twice: the layers'
 * settings records ({@code UiSettings}, {@code DbSettings}) validate what they get.
 */
@ConfigurationProperties(prefix = "project", ignoreUnknownFields = false)
public record ProjectProperties(EnvironmentKind environment, boolean requireEnvironment,
                                StandSettings stand, ContainersSettings containers, Api api, Ui ui, Domain domain,
                                Events events) {

    public ProjectProperties {
        Objects.requireNonNull(environment, "project.environment is missing");
        Objects.requireNonNull(stand, "project.stand is missing");
        Objects.requireNonNull(containers, "project.containers is missing");
        Objects.requireNonNull(api, "project.api is missing");
        Objects.requireNonNull(ui, "project.ui is missing");
        Objects.requireNonNull(domain, "project.domain is missing");
        Objects.requireNonNull(events, "project.events is missing");
    }

    /** Where the SUT comes from. An enum, not a free string: a typo fails binding with the allowed values. */
    public enum EnvironmentKind {
        /** Already running (compose, a shared QA stand): the run only connects. */
        STAND,
        /** Started by the run itself with Testcontainers: Postgres, Kafka and the SUT image; hermetic. */
        CONTAINERS
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
