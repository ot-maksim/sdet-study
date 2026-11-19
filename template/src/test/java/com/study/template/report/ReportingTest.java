package com.study.template.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.study.framework.api.ApiClient;
import com.study.framework.api.ApiSettings;
import com.study.framework.api.users.NewUsers;
import com.study.framework.core.data.Unique;
import com.study.framework.domain.UserSteps;
import com.study.framework.ui.users.FormError;
import com.study.framework.ui.users.ShownUser;
import com.study.framework.ui.users.UserForm;
import com.study.framework.ui.users.UsersScreen;
import com.sun.net.httpserver.HttpServer;
import io.qameta.allure.Allure;
import io.qameta.allure.AllureLifecycle;
import io.qameta.allure.AllureResultsWriter;
import io.qameta.allure.model.Attachment;
import io.qameta.allure.model.Parameter;
import io.qameta.allure.model.Status;
import io.qameta.allure.model.StepResult;
import io.qameta.allure.model.TestResult;
import io.qameta.allure.model.TestResultContainer;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.aspectj.lang.Aspects;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * What ends up in the report, without Docker: each test runs under its own Allure lifecycle writing to memory.
 *
 * <p>{@code @Isolated}: the lifecycle is global ({@code Allure.setLifecycle}); for the time of this class nothing
 * else runs, or parallel tests would report into it and lose their own steps — the one class of the module that
 * must not run in parallel, and JUnit says so instead of a comment.
 */
@Isolated
class ReportingTest {

    private final InMemoryResults results = new InMemoryResults();
    private AllureLifecycle previous;
    private String testCase;

    @BeforeEach
    void reportIntoMemory() {
        previous = Allure.getLifecycle();
        Allure.setLifecycle(new AllureLifecycle(results));
        testCase = UUID.randomUUID().toString();
        Allure.getLifecycle().scheduleTestCase(new TestResult().setUuid(testCase).setName("sample"));
        Allure.getLifecycle().startTestCase(testCase);
    }

    @AfterEach
    void restore() {
        Allure.setLifecycle(previous);
    }

    @Test
    void theAspectIsWoven() {
        assertThat(Aspects.hasAspect(ReportingAspect.class))
                .as("ReportingAspect is not woven: run with -javaagent:aspectjweaver (template/pom.xml, argLine)."
                        + " Without it every test passes and the report has no steps")
                .isTrue();
    }

    @Test
    void aFinalFrameworkClassGetsStepsWithoutKnowingIt() {
        UserSteps users = new UserSteps(null, new NewUsers(new Unique()), null, null);

        users.aNewUser();

        StepResult step = onlyStep();
        assertThat(step.getName()).isEqualTo("UserSteps.aNewUser");
        assertThat(step.getStatus()).isEqualTo(Status.PASSED);
        assertThat(step.getParameters()).extracting(Parameter::getName).containsExactly("result");
    }

    @Test
    void anApiExchangeIsAStepWithRequestAndResponse() throws Exception {
        HttpServer sut = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        sut.createContext("/api/users", exchange -> {
            byte[] body = "{\"id\":7}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(201, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        sut.start();
        try {
            ApiClient client = new ApiClient(new ApiSettings(
                    URI.create("http://localhost:" + sut.getAddress().getPort()), Duration.ofSeconds(5)));

            client.post("/api/users", Map.of("name", "Ada"));
        } finally {
            sut.stop(0);
        }

        StepResult step = onlyStep();
        assertThat(step.getName()).isEqualTo("POST /api/users -> 201");
        assertThat(step.getAttachments()).extracting(Attachment::getName).containsExactly("request body", "response");
        assertThat(results.attachment(step.getAttachments().get(1))).contains("{\"id\":7}");
    }

    @Test
    void aDecoratorIsAStepAndAFailureStaysTheTestsOwn() {
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
    void aFluentDecoratorReturnsItselfOrTheChainLeavesTheReport() {
        UsersScreen screen = new ReportingUsersScreen(new BlankScreen());

        screen.open().find("ada@example.com");

        assertThat(screen.open()).isSameAs(screen);
        assertThat(steps()).extracting(StepResult::getName)
                .startsWith("screen: open users", "screen: find ada@example.com");
    }

    private StepResult onlyStep() {
        List<StepResult> steps = steps();
        assertThat(steps).hasSize(1);
        return steps.get(0);
    }

    private List<StepResult> steps() {
        Allure.getLifecycle().stopTestCase(testCase);
        Allure.getLifecycle().writeTestCase(testCase);
        return results.tests.get(0).getSteps();
    }

    private static final class InMemoryResults implements AllureResultsWriter {

        final List<TestResult> tests = new CopyOnWriteArrayList<>();
        final Map<String, String> attachments = new java.util.concurrent.ConcurrentHashMap<>();

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
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
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
