package com.study.project.assembly.fixtures;

import static org.assertj.core.api.Assertions.assertThat;

import com.study.project.assembly.Groups;
import com.study.project.assembly.ProjectConfiguration;
import com.study.project.assembly.ProjectRetry;
import com.study.project.core.lifecycle.Cleanup;
import com.study.project.domain.UserSteps;
import com.study.project.environment.EnvironmentProvider;
import com.study.project.environment.SutEnvironment;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.testng.annotations.Test;

/**
 * Small test classes that {@code ProjectListenerTest} runs through a programmatic TestNG and asserts on their results.
 * Not run by the suite: testng.xml excludes this package (and Surefire's {@code -Dtest} skips nested classes).
 *
 * <p>Each one declares its own Spring configuration ({@code @SpringBootTest} + properties or an import) — and so gets
 * its own context: exactly what real test classes must not do (ArchitectureTest forbids Spring there).
 */
public final class ListenerFixtures {

    private ListenerFixtures() {
    }

    /** An environment that is always there and starts nothing: the listener is checked without Docker or a stand. */
    @TestConfiguration(proxyBeanMethods = false)
    public static class NoEnvironment {
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

    /**
     * {@code require-environment=false} stated, not inherited from the defaults: a CI run activates the Spring
     * profile {@code ci} for the whole JVM, and a skip test must not depend on how the run was started.
     */
    @Test(groups = Groups.API)
    @SpringBootTest(classes = ProjectConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
    @TestPropertySource(properties = {"project.environment=stand", "project.stand.sut-url=http://localhost:1",
            "project.stand.db-url=jdbc:postgresql://localhost:1/sut", "project.require-environment=false"})
    public static class StandDown {
        public void anything() {
        }
    }

    @Test(groups = Groups.API)
    @SpringBootTest(classes = ProjectConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
    @TestPropertySource(properties = {"project.environment=stand", "project.stand.sut-url=http://localhost:1",
            "project.stand.db-url=jdbc:postgresql://localhost:1/sut", "project.require-environment=true"})
    public static class StandDownInCi {
        public void anything() {
        }
    }

    @Test(groups = Groups.EVENTS)
    @SpringBootTest(classes = ProjectConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
    @Import(NoEnvironment.class)
    @TestPropertySource(properties = "project.require-environment=false")
    public static class EventsWithoutBroker {
        public void anything() {
        }
    }

    @Test(groups = Groups.EVENTS)
    @SpringBootTest(classes = ProjectConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
    @Import(NoEnvironment.class)
    @TestPropertySource(properties = "project.require-environment=true")
    public static class EventsWithoutBrokerInCi {
        public void anything() {
        }
    }

    @Test(groups = Groups.API)
    @SpringBootTest(classes = ProjectConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
    @Import(NoEnvironment.class)
    public static class TestBeansAsPlainParameters {
        public static volatile Cleanup seen;

        @Test
        public void first(Cleanup cleanup, Cleanup again) {
            assertThat(again).isSameAs(cleanup);
            seen = cleanup;
        }
    }

    @Test(groups = Groups.API)
    @SpringBootTest(classes = ProjectConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
    @Import(NoEnvironment.class)
    public static class StepsNeedTheEnvironment {
        @Test
        public void needsSteps(UserSteps users) {
        }
    }

    /**
     * Two methods of one instance at the same time ({@code parallel="methods"}): each gets its own test bean, and
     * each one's undo actions run after its own method.
     */
    @Test(groups = Groups.API)
    @SpringBootTest(classes = ProjectConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
    @Import(NoEnvironment.class)
    public static class ParallelMethods {
        public static final Set<Cleanup> SEEN = ConcurrentHashMap.newKeySet();
        public static final List<String> UNDONE = new CopyOnWriteArrayList<>();

        @Test
        public void first(Cleanup cleanup) throws InterruptedException {
            SEEN.add(cleanup);
            cleanup.defer("first", () -> UNDONE.add("first"));
            Thread.sleep(200);   // overlap with the other method
        }

        @Test
        public void second(Cleanup cleanup) throws InterruptedException {
            SEEN.add(cleanup);
            cleanup.defer("second", () -> UNDONE.add("second"));
            Thread.sleep(200);
        }
    }

    /** Retried once on its own (CI attaches {@link ProjectRetry} to every test through the listener). */
    public static class OneRetry extends ProjectRetry {
        public OneRetry() {
            super(1);
        }
    }

    /** Fails the first time, passes the second: flaky. A component test — retry is not about the project context. */
    public static class FlakyOnce {
        public static final AtomicInteger ATTEMPTS = new AtomicInteger();

        @Test(retryAnalyzer = OneRetry.class)
        public void sometimes() {
            assertThat(ATTEMPTS.incrementAndGet()).as("attempt").isGreaterThan(1);
        }
    }
}
