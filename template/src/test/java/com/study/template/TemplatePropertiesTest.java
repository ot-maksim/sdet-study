package com.study.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.study.template.TemplateProperties.Domain.Channel;
import com.study.template.TemplateProperties.EnvironmentKind;
import com.study.template.environment.ContainersEnvironment;
import com.study.template.environment.EnvironmentProvider;
import com.study.template.environment.StandEnvironment;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

/**
 * Configuration as Spring Boot loads it for the tests (application.yml + profiles + overrides), without starting
 * any environment: beans are lazy, and choosing a provider does not start it.
 *
 * <p>Hermetic: the machine's system properties and environment variables are not layers here, or a run with
 * {@code -Dspring.profiles.active=ci} (CI) or {@code SPRING_PROFILES_ACTIVE=stand} would change what "the
 * defaults" are and fail {@link #defaultsAreHermetic} for a reason that is not in this class.
 */
class TemplatePropertiesTest {

    @Test
    void defaultsAreHermetic() {
        try (ConfigurableApplicationContext spring = start(new String[0])) {
            TemplateProperties properties = spring.getBean(TemplateProperties.class);

            assertThat(properties.environment()).isEqualTo(EnvironmentKind.CONTAINERS);
            assertThat(properties.requireEnvironment()).isFalse();
            assertThat(spring.getBean(EnvironmentProvider.class)).isInstanceOf(ContainersEnvironment.class);
        }
    }

    @Test
    void profilesAreSlicesThatCombine() {
        try (ConfigurableApplicationContext spring = start(new String[] {"stand", "mobile", "db", "ci"})) {
            TemplateProperties properties = spring.getBean(TemplateProperties.class);

            assertThat(spring.getBean(EnvironmentProvider.class)).isInstanceOf(StandEnvironment.class);
            assertThat(properties.ui().profile()).isEqualTo("mobile");
            assertThat(properties.domain().userPrecondition()).isEqualTo(Channel.DB);
            assertThat(properties.requireEnvironment()).isTrue();
        }
    }

    @Test
    void aMistypedKeyFailsInsteadOfBeingIgnored() {
        try (ConfigurableApplicationContext spring = start(new String[0], "--template.ui.max-browser=2")) {
            assertThatThrownBy(() -> spring.getBean(TemplateProperties.class))
                    .satisfies(e -> assertThat(NestedExceptionUtils.getMostSpecificCause(e))
                            .hasMessageContaining("template.ui.max-browser"));
        }
    }

    @Test
    void aMistypedEnvironmentFailsAtBinding() {
        try (ConfigurableApplicationContext spring = start(new String[0], "--template.environment=contaners")) {
            assertThatThrownBy(() -> spring.getBean(EnvironmentProvider.class))
                    .hasStackTraceContaining("contaners");
        }
    }

    @Test
    void thePasswordIsASecretFromTheStart() {
        try (ConfigurableApplicationContext spring = start(new String[] {"stand"})) {
            TemplateProperties.Stand stand = spring.getBean(TemplateProperties.class).stand();

            assertThat(stand.toString()).contains("dbPassword=****").doesNotContain("=sut]");
            assertThat(stand.dbPassword().reveal()).isEqualTo("sut");
        }
    }

    private static ConfigurableApplicationContext start(String[] profiles, String... args) {
        // Command-line style arguments: the highest layer, as -D in a real run
        return new SpringApplicationBuilder(TemplateConfiguration.class)
                .environment(new MachineIndependentEnvironment())
                .web(WebApplicationType.NONE)
                .profiles(profiles)
                .logStartupInfo(false)
                .run(args);
    }

    /** Application files and the arguments of {@link #start} only: no system properties, no environment variables. */
    private static final class MachineIndependentEnvironment extends StandardEnvironment {

        @Override
        protected void customizePropertySources(MutablePropertySources sources) {
        }
    }
}
