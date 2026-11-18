package com.study.principles.dry;

import com.study.principles.dry.bad.OverDriedScenarios;
import com.study.principles.dry.bad.TestData;
import com.study.principles.dry.good.DampScenarios;
import com.study.principles.dry.wet.WetScenarios;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;
import static org.junit.platform.testkit.engine.EventConditions.event;
import static org.junit.platform.testkit.engine.EventConditions.finishedWithFailure;
import static org.junit.platform.testkit.engine.EventConditions.test;
import static org.junit.platform.testkit.engine.TestExecutionResultConditions.message;

/**
 * Runs the three styles of the same user tests ({@code *Scenarios} classes) through the JUnit engine against
 * good and broken builds of the app, and compares what happens.
 */
class DryTest {

    private static final String USERS_V1 = "\"/api/v1/users\"";

    @BeforeEach
    void cleanStart() {
        TestData.restoreTemplate();
        Deployed.reset();
    }

    @AfterEach
    void cleanEnd() {
        cleanStart();
    }

    @Nested
    class OverDried {

        @Test
        void aTweakToTheSharedTemplateBreaksALaterUnrelatedTest() {
            Events events = run(selectClass(OverDriedScenarios.class));

            events.assertStatistics(stats -> stats.started(5).failed(1));
            events.failed().assertThatEvents().haveExactly(1,
                    event(test("userIsCreated"), finishedWithFailure(message(m -> m.contains("400")))));
        }

        @Test
        void theSameTestPassesAlone() {
            run(selectMethod(OverDriedScenarios.class, "userIsCreated"))
                    .assertStatistics(stats -> stats.started(1).succeeded(1));
        }

        @Test
        void expectationsDerivedFromTheAppStayGreenWhenTheRuleIsBroken() {
            Deployed.deploy(Release.CURRENT.withMinAge(21));

            run(selectMethod(OverDriedScenarios.class, "adultIsAccepted"),
                    selectMethod(OverDriedScenarios.class, "minorIsRejected"))
                    .assertStatistics(stats -> stats.started(2).succeeded(2));
        }

        @Test
        void aFailureSaysOnlyThatSomethingDiffers() {
            Deployed.deploy(Release.CURRENT.losingCountry());

            String message = failureMessage(run(selectMethod(OverDriedScenarios.class, "userIsCreated")));

            assertThat(message).contains("Expecting value to be true but was false").doesNotContain("country");
        }
    }

    @Nested
    class Wet {

        @Test
        void passesAndReadsOnItsOwn() {
            run(selectClass(WetScenarios.class)).assertStatistics(stats -> stats.started(4).succeeded(4));
        }

        @Test
        void anEndpointRenameIsOneEditPerTest() throws IOException {
            Deployed.deploy(Release.CURRENT.withUsersPath("/api/v2/users"));

            run(selectClass(WetScenarios.class)).assertStatistics(stats -> stats.started(4).failed(4));
            assertThat(occurrences(USERS_V1, testSource("wet/WetScenarios.java"))).isEqualTo(4);
        }
    }

    @Nested
    class Damp {

        @Test
        void passes() {
            run(selectClass(DampScenarios.class)).assertStatistics(stats -> stats.started(5).succeeded(5));
        }

        @ParameterizedTest(name = "random order, seed {0}")
        @ValueSource(longs = {1, 42, 2026})
        void testsAreIndependentOfOrder(long seed) {
            EngineTestKit.engine("junit-jupiter")
                    .configurationParameter("junit.jupiter.testmethod.order.default",
                            "org.junit.jupiter.api.MethodOrderer$Random")
                    .configurationParameter("junit.jupiter.execution.order.random.seed", String.valueOf(seed))
                    .selectors(selectClass(DampScenarios.class))
                    .execute().testEvents()
                    .assertStatistics(stats -> stats.started(5).succeeded(5));
        }

        @Test
        void literalExpectationsCatchTheBrokenRule() {
            Deployed.deploy(Release.CURRENT.withMinAge(21));

            Events events = run(selectClass(DampScenarios.class));

            events.assertStatistics(stats -> stats.failed(1));
            events.failed().assertThatEvents().haveExactly(1, event(test("anAdultOf18IsAccepted")));
        }

        @Test
        void aFailureNamesTheFieldTheExpectationAndTheResponse() {
            Deployed.deploy(Release.CURRENT.losingCountry());

            String message = failureMessage(run(selectMethod(DampScenarios.class, "countryIsStored")));

            assertThat(message).contains("<country> to be <DE> but was <US>", "201 {age=30");
        }

        @Test
        void anEndpointRenameIsOneEdit() throws IOException {
            Deployed.deploy(Release.CURRENT.withUsersPath("/api/v2/users"));

            run(selectClass(DampScenarios.class)).assertStatistics(stats -> stats.failed(4));
            long inGood = Stream.concat(
                            mainSources("good").stream(),
                            Stream.of(testSource("good/DampScenarios.java")))
                    .mapToLong(source -> occurrences(USERS_V1, source))
                    .sum();
            assertThat(inGood).as("the path lives in UserApi only").isEqualTo(1);
        }
    }

    // ---- helpers ---------------------------------------------------------------------------------------

    private static Events run(DiscoverySelector... selectors) {
        return EngineTestKit.engine("junit-jupiter").selectors(selectors).execute().testEvents();
    }

    private static String failureMessage(Events events) {
        events.assertStatistics(stats -> stats.failed(1));
        return events.failed().stream()
                .map(e -> e.getPayload(org.junit.platform.engine.TestExecutionResult.class).orElseThrow())
                .map(result -> result.getThrowable().orElseThrow().getMessage())
                .findFirst().orElseThrow();
    }

    private static final Path PACKAGE = Path.of("com", "study", "principles", "dry");

    private static String testSource(String relative) {
        return read(Path.of("src", "test", "java").resolve(PACKAGE).resolve(relative));
    }

    private static List<String> mainSources(String subPackage) throws IOException {
        try (Stream<Path> files = Files.list(Path.of("src", "main", "java").resolve(PACKAGE).resolve(subPackage))) {
            return files.map(DryTest::read).toList();
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new IllegalStateException("run from the principles module directory: " + file.toAbsolutePath(), e);
        }
    }

    private static long occurrences(String needle, String text) {
        return Arrays.stream(text.split(java.util.regex.Pattern.quote(needle), -1)).count() - 1;
    }
}
