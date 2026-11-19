package com.study.template;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.testkit.engine.EventConditions.container;
import static org.junit.platform.testkit.engine.EventConditions.finishedWithFailure;
import static org.junit.platform.testkit.engine.EventConditions.skippedWithReason;
import static org.junit.platform.testkit.engine.EventConditions.test;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.message;

import com.study.framework.core.lifecycle.Cleanup;
import com.study.framework.domain.UserSteps;
import com.study.template.environment.EnvironmentProvider;
import com.study.template.environment.SutEnvironment;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;

/**
 * The environment policy and the scope guard, end to end through JUnit + Spring: small test classes (nested, so
 * Surefire does not pick them up) run by the engine. Each nested class has its own properties — and so its own
 * Spring context: exactly what real test classes should avoid (see {@link TemplateTest}).
 */
class TemplateExtensionTest {

    @ApiTests
    @TestPropertySource(properties = {"template.environment=stand", "template.stand.sut-url=http://localhost:1",
            "template.stand.db-url=jdbc:postgresql://localhost:1/sut"})
    static class StandDown {
        @Test
        void anything() {
        }
    }

    @ApiTests
    @TestPropertySource(properties = {"template.environment=stand", "template.stand.sut-url=http://localhost:1",
            "template.stand.db-url=jdbc:postgresql://localhost:1/sut", "template.require-environment=true"})
    static class StandDownInCi {
        @Test
        void anything() {
        }
    }

    /** An environment that is always there and starts nothing: the scope guard is checked without Docker or a stand. */
    @TestConfiguration(proxyBeanMethods = false)
    static class NoEnvironment {
        @Bean
        @Primary
        EnvironmentProvider alwaysAvailable() {
            return new EnvironmentProvider() {
                @Override
                public Optional<String> unavailable() {
                    return Optional.empty();
                }

                @Override
                public SutEnvironment start() {
                    throw new AssertionError("the guard must not need the environment");
                }

                @Override
                public Optional<String> eventsUnavailable() {
                    return Optional.of("no broker in this fake");
                }
            };
        }
    }

    @ApiTests
    @Import(NoEnvironment.class)
    static class TestBeanInBeforeAll {
        @BeforeAll
        static void once(Cleanup cleanup) {
        }

        @Test
        void anything() {
        }
    }

    @ApiTests
    @Import(NoEnvironment.class)
    static class TestBeansAsPlainParameters {
        static Cleanup seen;

        @Test
        void first(Cleanup cleanup, Cleanup again) {
            assertThat(again).isSameAs(cleanup);
            seen = cleanup;
        }
    }

    @Test
    void unavailableStandSkipsWithTheReason() {
        execute(StandDown.class).allEvents().assertThatEvents()
                .haveExactly(1, container(skippedWithReason(reason -> reason.startsWith("Stand is down")
                        && reason.endsWith("(set template.require-environment=true to fail instead)"))));
    }

    @Test
    void unavailableStandFailsWhenRequired() {
        execute(StandDownInCi.class).allEvents().assertThatEvents()
                .haveExactly(1, container(finishedWithFailure(message(m -> m.contains("Stand is down")
                        && m.endsWith("(template.require-environment=true)")))));
    }

    @EventTests
    @Import(NoEnvironment.class)
    static class EventsWithoutBroker {
        @Test
        void anything() {
        }
    }

    @EventTests
    @Import(NoEnvironment.class)
    @TestPropertySource(properties = "template.require-environment=true")
    static class EventsWithoutBrokerInCi {
        @Test
        void anything() {
        }
    }

    @Test
    void environmentWithoutBrokerSkipsEventTests() {
        execute(EventsWithoutBroker.class).allEvents().assertThatEvents()
                .haveExactly(1, container(skippedWithReason(reason -> reason.startsWith("no broker in this fake"))));
    }

    @Test
    void environmentWithoutBrokerFailsEventTestsWhenRequired() {
        execute(EventsWithoutBrokerInCi.class).allEvents().assertThatEvents()
                .haveExactly(1, container(finishedWithFailure(message(m -> m.contains("no broker in this fake")
                        && m.endsWith("(template.require-environment=true)")))));
    }

    @Test
    void aTestBeanInBeforeAllIsRefused() {
        execute(TestBeanInBeforeAll.class).allEvents().assertThatEvents()
                .haveExactly(1, container(finishedWithFailure(message(m -> m.contains("'cleanup' lives per test")))));
    }

    @Test
    void parametersNeedNoAutowired() {
        execute(TestBeansAsPlainParameters.class).testEvents().assertStatistics(stats -> stats.succeeded(1));
        assertThat(TestBeansAsPlainParameters.seen).isNotNull();
    }

    @Test
    void stepsAreResolvedThroughTheSameRoute() {
        // UserSteps needs the environment (UserApi → SutEnvironment): with NoEnvironment it fails at start(),
        // which proves that parameters really come from the template's graph and not from a test double
        Events events = execute(StepsNeedTheEnvironment.class).testEvents();
        events.assertThatEvents().haveExactly(1, test(finishedWithFailure(message(m -> m.contains("UserSteps")))));
    }

    @ApiTests
    @Import(NoEnvironment.class)
    static class StepsNeedTheEnvironment {
        @Test
        void needsSteps(UserSteps users) {
        }
    }

    private static EngineExecutionResults execute(Class<?> testClass) {
        return EngineTestKit.engine("junit-jupiter").selectors(selectClass(testClass)).execute();
    }
}
