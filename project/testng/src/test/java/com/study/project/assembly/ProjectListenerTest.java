package com.study.project.assembly;

import static org.assertj.core.api.Assertions.assertThat;

import com.study.project.assembly.fixtures.ListenerFixtures.EventsWithoutBroker;
import com.study.project.assembly.fixtures.ListenerFixtures.EventsWithoutBrokerInCi;
import com.study.project.assembly.fixtures.ListenerFixtures.FlakyOnce;
import com.study.project.assembly.fixtures.ListenerFixtures.ParallelMethods;
import com.study.project.assembly.fixtures.ListenerFixtures.StandDown;
import com.study.project.assembly.fixtures.ListenerFixtures.StandDownInCi;
import com.study.project.assembly.fixtures.ListenerFixtures.StepsNeedTheEnvironment;
import com.study.project.assembly.fixtures.ListenerFixtures.TestBeansAsPlainParameters;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.testng.ITestResult;
import org.testng.TestListenerAdapter;
import org.testng.TestNG;
import org.testng.annotations.Test;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

/**
 * The listener end to end through TestNG + Spring: the fixtures ({@code assembly.fixtures}) run in a programmatic
 * TestNG and the assertions are on their results; the JUnit twin does the same with the engine test kit.
 *
 * <p>The nested run gets a listener of its own instead of the ServiceLoader one: that one reads the build's settings
 * (CI retries every failure once, the flaky list is the build's file), and the fixtures fail on purpose. That the
 * ServiceLoader registration works is shown by every project test of the suite.
 */
public class ProjectListenerTest {

    /** Allure's listener comes through the same ServiceLoader: the fixtures' red results must not reach the report. */
    private static final String ALLURE = "io.qameta.allure.testng.AllureTestNg";

    private static final int NO_RETRIES = 0;

    @Test
    public void unavailableStandSkipsWithTheReason() {
        ITestResult result = only(run(StandDown.class).getSkippedTests());

        assertThat(result.getThrowable()).hasMessageStartingWith("Stand is down")
                .hasMessageEndingWith("(set project.require-environment=true to fail instead)");
    }

    @Test
    public void unavailableStandFailsWhenRequired() {
        ITestResult result = only(run(StandDownInCi.class).getFailedTests());

        assertThat(result.getThrowable()).hasMessageContaining("Stand is down")
                .hasMessageEndingWith("(project.require-environment=true)");
    }

    @Test
    public void environmentWithoutBrokerSkipsEventTests() {
        assertThat(only(run(EventsWithoutBroker.class).getSkippedTests()).getThrowable())
                .hasMessageStartingWith("no broker in this fake");
    }

    @Test
    public void environmentWithoutBrokerFailsEventTestsWhenRequired() {
        assertThat(only(run(EventsWithoutBrokerInCi.class).getFailedTests()).getThrowable())
                .hasMessageContaining("no broker in this fake")
                .hasMessageEndingWith("(project.require-environment=true)");
    }

    @Test
    public void parametersNeedNoAutowired() {
        TestListenerAdapter results = run(TestBeansAsPlainParameters.class);

        assertThat(results.getFailedTests()).isEmpty();
        assertThat(results.getPassedTests()).hasSize(1);
        assertThat(TestBeansAsPlainParameters.seen).isNotNull();
    }

    @Test
    public void theReportNamesTheMethodWithoutTheDataProvidersRow() {
        ITestResult result = only(run(TestBeansAsPlainParameters.class).getPassedTests());

        assertThat(result.getParameters()).isEmpty();
    }

    @Test
    public void stepsAreResolvedThroughTheSameRoute() {
        // UserSteps needs the environment (UserApi → SutEnvironment): with NoEnvironment it fails at start(),
        // which proves that parameters really come from the project's graph and not from a test double
        ITestResult result = only(run(StepsNeedTheEnvironment.class).getFailedTests());

        assertThat(result.getThrowable()).hasMessageContaining("Cannot resolve UserSteps for needsSteps");
    }

    @Test
    public void parallelMethodsOfOneInstanceGetTheirOwnTestBeans() {
        TestListenerAdapter results = run(suite(2, ParallelMethods.class));

        assertThat(results.getPassedTests()).hasSize(2);
        assertThat(ParallelMethods.SEEN).as("one Cleanup per method, not per instance").hasSize(2);
        assertThat(ParallelMethods.UNDONE).containsExactlyInAnyOrder("first", "second");
    }

    @Test
    public void moreThreadsThanBrowsersFailsBeforeWaitingForABrowser() {
        ITestResult result = only(run(suite(9, TestBeansAsPlainParameters.class)).getFailedTests());

        assertThat(result.getThrowable()).hasMessageContaining("runs up to 9 tests at once")
                .hasMessageContaining("project.ui.max-browsers");
    }

    @Test
    public void aTestThatPassesOnlyOnARetryIsListedAsFlaky() throws Exception {
        Path report = Files.createTempFile("flaky-", ".txt");
        Files.delete(report);

        TestNG testng = testng(new ProjectListener(NO_RETRIES, Optional.of(report)));   // FlakyOnce retries by itself
        testng.setTestClasses(new Class<?>[] {FlakyOnce.class});
        TestListenerAdapter results = run(testng);

        assertThat(results.getPassedTests()).hasSize(1);
        assertThat(only(results.getSkippedTests()).wasRetried()).as("the failed attempt stays, marked retried").isTrue();
        assertThat(Files.readAllLines(report)).containsExactly(FlakyOnce.class.getName() + ".sometimes");
    }

    private static TestListenerAdapter run(Class<?> fixture) {
        TestNG testng = testng(new ProjectListener(NO_RETRIES, Optional.empty()));
        testng.setTestClasses(new Class<?>[] {fixture});
        return run(testng);
    }

    private static TestListenerAdapter run(XmlSuite suite) {
        TestNG testng = testng(new ProjectListener(NO_RETRIES, Optional.empty()));
        testng.setXmlSuites(List.of(suite));
        return run(testng);
    }

    private static TestNG testng(ProjectListener listener) {
        TestNG testng = new TestNG(false);   // no default reporters
        testng.setListenersToSkipFromBeingWiredInViaServiceLoaders(ALLURE, ProjectListener.class.getName());
        testng.addListener(listener);
        testng.setOutputDirectory("target/fixtures-output");
        testng.setVerbose(0);
        return testng;
    }

    private static TestListenerAdapter run(TestNG testng) {
        TestListenerAdapter results = new TestListenerAdapter();
        testng.addListener(results);
        testng.run();
        return results;
    }

    private static XmlSuite suite(int threads, Class<?> fixture) {
        XmlSuite suite = new XmlSuite();
        suite.setName("fixture");
        XmlTest test = new XmlTest(suite);
        test.setName(fixture.getSimpleName());
        test.setParallel(threads > 1 ? XmlSuite.ParallelMode.METHODS : XmlSuite.ParallelMode.NONE);
        test.setThreadCount(threads);
        test.setXmlClasses(List.of(new XmlClass(fixture)));
        return suite;
    }

    private static ITestResult only(List<ITestResult> results) {
        assertThat(results).hasSize(1);
        return results.get(0);
    }
}
