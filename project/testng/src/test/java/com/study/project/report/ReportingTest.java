package com.study.project.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.study.project.api.ApiSettings;
import com.study.project.api.ApiTransport;
import com.study.project.api.ContractViolation;
import com.study.project.api.users.NewUsers;
import com.study.project.api.users.UserApi;
import com.study.project.assembly.Groups;
import com.study.project.core.Unique;
import com.study.project.ui.users.FormError;
import com.study.project.ui.users.ShownUser;
import com.study.project.ui.users.UserForm;
import com.study.project.ui.users.UsersScreen;
import com.sun.net.httpserver.HttpServer;
import io.qameta.allure.Allure;
import io.qameta.allure.AllureLifecycle;
import io.qameta.allure.AllureResultsWriter;
import io.qameta.allure.model.Attachment;
import io.qameta.allure.model.Status;
import io.qameta.allure.model.StepResult;
import io.qameta.allure.model.TestResult;
import io.qameta.allure.model.TestResultContainer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * What ends up in the report, without Docker: each test runs under its own Allure lifecycle writing to memory.
 *
 * <p>Group {@link Groups#ISOLATED}: the lifecycle is global ({@code Allure.setLifecycle}); testng.xml runs this class
 * in its own {@code <test>}, alone, or parallel tests would report into it and lose their own steps. Its methods share
 * one instance and its fields, so they run one after another there.
 */
@Test(groups = Groups.ISOLATED, singleThreaded = true)
public class ReportingTest {

    private InMemoryResults results;
    private AllureLifecycle previous;
    private String testCase;

    @BeforeMethod
    public void reportIntoMemory() {
        results = new InMemoryResults();   // one instance for all methods: fresh per test by hand
        previous = Allure.getLifecycle();
        Allure.setLifecycle(new AllureLifecycle(results));
        testCase = UUID.randomUUID().toString();
        Allure.getLifecycle().scheduleTestCase(new TestResult().setUuid(testCase).setName("sample"));
        Allure.getLifecycle().startTestCase(testCase);
    }

    @AfterMethod(alwaysRun = true)
    public void restore() {
        Allure.setLifecycle(previous);
    }

    @Test
    public void anApiExchangeIsAttachedWithoutTheApiLayerKnowingAllure() throws Exception {
        HttpServer sut = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        sut.createContext("/api/users", exchange -> {
            byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        sut.start();
        try {
            ApiSettings settings = new ApiSettings(URI.create("http://localhost:" + sut.getAddress().getPort()), Duration.ofSeconds(5));
            new UserApi(new ApiTransport(settings, List.of(ApiReport.exchanges()))).all();
        } finally {
            sut.stop(0);
        }

        TestResult test = written();
        assertThat(test.getAttachments()).hasSize(2);
        assertThat(results.attachment(test.getAttachments().get(0))).contains("/api/users");
        assertThat(results.attachment(test.getAttachments().get(1))).contains("200");
    }

    @Test
    public void aContractViolationIsABrokenStepAndTheTestKeepsItsOwnVerdict() {
        new ContractReport().accept(new ContractViolation("GET /api/users/7 -> 200", List.of("Object has missing required properties ([\"createdAt\"])")));

        TestResult test = written();
        assertThat(test.getStatus()).isNotIn(Status.FAILED, Status.BROKEN);
        assertThat(test.getSteps()).singleElement().satisfies(step -> {
            assertThat(step.getName()).isEqualTo("contract violation: GET /api/users/7 -> 200");
            assertThat(step.getStatus()).isEqualTo(Status.BROKEN);
            assertThat(step.getStatusDetails().getMessage()).contains("createdAt");
        });
    }

    @Test
    public void aDecoratorIsAStepAndAFailureStaysTheTestsOwn() {
        IllegalStateException cause = new IllegalStateException("SUT said 500");
        ReportingUserPrecondition precondition = new ReportingUserPrecondition(user -> {
            throw cause;
        }, "api");

        assertThatThrownBy(() -> precondition.create(new NewUsers(new Unique()).valid())).isSameAs(cause);

        StepResult step = onlyStep();
        assertThat(step.getName()).isEqualTo("precondition: a user through api");
        assertThat(step.getStatus()).isEqualTo(Status.BROKEN);
    }

    @Test
    public void aFluentDecoratorReturnsItselfOrTheChainLeavesTheReport() {
        UsersScreen screen = new ReportingUsersScreen(new BlankScreen());

        screen.open().find("ada@example.com");

        assertThat(screen.open()).isSameAs(screen);
        assertThat(written().getSteps()).extracting(StepResult::getName)
                .startsWith("screen: open users", "screen: find ada@example.com");
    }

    private StepResult onlyStep() {
        List<StepResult> steps = written().getSteps();
        assertThat(steps).hasSize(1);
        return steps.get(0);
    }

    private TestResult written() {
        Allure.getLifecycle().stopTestCase(testCase);
        Allure.getLifecycle().writeTestCase(testCase);
        return results.tests.get(0);
    }

    private static final class InMemoryResults implements AllureResultsWriter {

        final List<TestResult> tests = new CopyOnWriteArrayList<>();
        final Map<String, String> attachments = new ConcurrentHashMap<>();

        @Override
        public void write(TestResult result) {
            tests.add(result);
        }

        @Override
        public void write(TestResultContainer container) {
        }

        @Override
        public void write(String source, InputStream content) {
            try (content) {
                attachments.put(source, new String(content.readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        String attachment(Attachment attachment) {
            return attachments.get(attachment.getSource());
        }
    }

    /** A screen that does nothing: the decorator is the subject here, not the page. */
    private static final class BlankScreen implements UsersScreen {

        @Override
        public UsersScreen open() {
            return this;
        }

        @Override
        public ShownUser create(UserForm user) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FormError createRejected(UserForm user) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ShownUser> find(String email) {
            return Optional.empty();
        }

        @Override
        public void delete(String email) {
        }

        @Override
        public Optional<String> notice() {
            return Optional.empty();
        }
    }
}
