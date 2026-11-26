package com.study.project.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.study.project.api.users.NewUser;
import com.study.project.api.users.User;
import com.study.project.api.users.UserApi;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

/**
 * The contract check on a fake SUT that answers what each test tells it to (no Docker): a response that drifts from
 * {@code openapi.yaml} is a violation — reported by the filter without failing the call, failed by
 * {@code conformsTo} in a contract test (ADR 0015); a request that breaks the contract on purpose (a negative test)
 * is neither.
 *
 * <p>{@code singleThreaded}: TestNG makes one instance for all methods, and the fake SUT lives in fields; its methods
 * run one after another (the class still runs next to the others).
 */
@Test(singleThreaded = true)
public class ContractFilterTest {

    private static final String ADA = """
            {"id":7,"name":"Ada","email":"ada@example.test","age":30,"country":"DE","createdAt":"2026-11-22T10:15:30.123456Z"}""";

    private final Contract contract = Contract.fromClasspath("contract/openapi.yaml");
    private final AtomicReference<Answer> answer = new AtomicReference<>();
    private ContractCoverage coverage;
    private final List<ContractViolation> reported = new CopyOnWriteArrayList<>();
    private HttpServer sut;
    private ApiTransport transport;
    private UserApi users;

    private record Answer(int status, String body) {
    }

    @BeforeMethod
    public void startFakeSut() throws IOException {
        coverage = new ContractCoverage(contract);   // one instance for all methods: fresh per test by hand
        reported.clear();
        sut = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        sut.createContext("/", exchange -> {
            Answer next = answer.get();
            byte[] body = next.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("Location", "/api/users/7");
            exchange.getResponseHeaders().add("Link", "</api/users?page=2>; rel=\"next\"");
            exchange.getResponseHeaders().add("Link", "</api/users?page=0>; rel=\"prev\"");
            exchange.sendResponseHeaders(next.status(), next.body().isEmpty() ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        sut.start();
        ApiSettings settings = new ApiSettings(URI.create("http://localhost:" + sut.getAddress().getPort()), Duration.ofSeconds(5));
        transport = new ApiTransport(settings, List.of(new ContractFilter(contract, coverage, reported::add)));
        users = new UserApi(transport);
    }

    @AfterMethod(alwaysRun = true)
    public void stopFakeSut() {
        sut.stop(0);
    }

    @Test
    public void aResponseAsTheContractSaysIsMappedToTheProjectsRecordAndNothingIsReported() {
        answer.set(new Answer(200, ADA));

        assertThat(users.get(7)).isEqualTo(new User(7, "Ada", "ada@example.test", 30, "DE",
                Instant.parse("2026-11-22T10:15:30.123456Z")));
        assertThat(reported).isEmpty();
        ApiResponseAssert.assertThat(users.tryGet(7)).hasStatus(200).conformsTo(contract);
    }

    @Test
    public void aMissingRequiredFieldIsReportedWithoutFailingTheCall() {
        answer.set(new Answer(200, ADA.replace(",\"createdAt\":\"2026-11-22T10:15:30.123456Z\"", "")));

        ApiResponse response = users.tryGet(7);

        assertThat(response.status()).isEqualTo(200);
        assertThat(reported).singleElement().satisfies(violation -> {
            assertThat(violation.call()).isEqualTo("GET /api/users/7 -> 200");
            assertThat(violation.toString()).contains("breaks the SUT's contract").contains("createdAt");
        });
    }

    @Test
    public void aContractTestFailsOnTheSameDriftWithTheReportAndTheExchange() {
        answer.set(new Answer(200, ADA.replace(",\"createdAt\":\"2026-11-22T10:15:30.123456Z\"", "")));

        assertThatThrownBy(() -> ApiResponseAssert.assertThat(users.tryGet(7)).hasStatus(200).conformsTo(contract))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("keep the SUT's contract")
                .hasMessageContaining("createdAt")
                .hasMessageContaining("GET /api/users/7");
    }

    @Test
    public void aNewErrorCodeIsAContractChange() {
        answer.set(new Answer(400, "{\"error\":\"age_negative\",\"message\":\"Age must not be negative\"}"));

        ApiResponse response = users.tryCreate(new NewUser("Ada", "ada@example.test", -1, "DE"));

        assertThat(contract.violations(response)).anySatisfy(problem -> assertThat(problem).contains("age_negative"));
        assertThat(reported).hasSize(1);
    }

    @Test
    public void aStatusTheOperationDoesNotDeclareIsAContractChange() {
        answer.set(new Answer(200, ADA));

        ApiResponse response = users.tryCreate(new NewUser("Ada", "ada@example.test", 30, "DE"));

        assertThat(contract.violations(response)).anySatisfy(problem -> assertThat(problem).contains("200"));
        assertThat(reported).singleElement().extracting(ContractViolation::call).isEqualTo("POST /api/users -> 200");
    }

    /** The query is not part of the path an operation is found by. */
    @Test
    public void aCallWithAQueryIsCheckedAgainstItsOperation() {
        answer.set(new Answer(200, "[" + ADA + "]"));

        ApiResponseAssert.assertThat(users.tryFindByEmail("ada@example.test")).hasStatus(200).conformsTo(contract);
        assertThat(reported).isEmpty();
    }

    @Test
    public void aRequestTheContractForbidsIsSentForTheSutToReject() {
        answer.set(new Answer(400, "{\"error\":\"age_too_low\",\"message\":\"Age must be at least 18\"}"));

        ApiResponse response = users.tryCreate(new NewUser("Kid", "kid@example.test", 17, null));

        assertThat(response.isError(400, "age_too_low")).isTrue();
        assertThat(response.toString()).contains("POST /api/users").contains("\"age\":17").doesNotContain("country");
        assertThat(reported).isEmpty();
    }

    /** What the contract tests send that the client would never build. */
    @Test
    public void malformedJsonAndAnIdThatIsNotANumberGoOutAsWritten() {
        answer.set(new Answer(400, "{\"error\":\"malformed_json\",\"message\":\"Body is not a valid user JSON\"}"));
        assertThat(users.tryCreate("{\"name\":").toString()).contains("POST /api/users {\"name\":");

        answer.set(new Answer(400, "{\"error\":\"id_invalid\",\"message\":\"Id must be a number: abc\"}"));
        ApiResponseAssert.assertThat(users.tryGet("abc")).hasStatus(400).conformsTo(contract);
        assertThat(users.tryDelete("abc").toString()).contains("DELETE /api/users/abc");
        assertThat(reported).isEmpty();

        // The path still finds its operation, so the check is not vacuous: a body without "message" breaks it
        answer.set(new Answer(400, "{\"error\":\"id_invalid\"}"));
        assertThat(contract.violations(users.tryGet("abc"))).anySatisfy(problem -> assertThat(problem).contains("message"));
    }

    @Test
    public void theCoverageCountsEveryCallByTheOperationItGoesTo() {
        answer.set(new Answer(200, ADA));
        users.tryGet(7);
        users.tryGet(8);
        answer.set(new Answer(400, "{\"error\":\"id_invalid\",\"message\":\"Id must be a number: abc\"}"));
        users.tryDelete("abc");

        assertThat(coverage.calls()).isEqualTo(3);
        assertThat(coverage.missing()).contains("GET /health 200", "POST /api/users 201", "DELETE /api/users/{id} 204")
                .doesNotContain("GET /api/users/{id} 200", "DELETE /api/users/{id} 400");
        assertThat(coverage.report())
                .contains("2 of 11 responses of openapi.yaml seen in 3 calls")
                .contains("| GET /api/users/{id} 200 | 2 |")
                .contains("| DELETE /api/users/{id} 400 | 1 |")
                .contains("| GET /health 200 | 0 — never seen |")
                .doesNotContain("not in the contract");
    }

    @Test
    public void aResponseTheContractDoesNotDeclareIsListedApart() {
        answer.set(new Answer(200, ADA));
        users.tryCreate(new NewUser("Ada", "ada@example.test", 30, "DE"));
        transport.send(api -> api.users().getUser().reqSpec(spec -> spec.setBasePath("/api/admin")).idPath(7));

        assertThat(coverage.undeclared()).containsExactly("GET /api/admin/api/users/7 200", "POST /api/users 200");
        assertThat(coverage.report()).contains("Seen but not in the contract").contains("- POST /api/users 200 — 1 calls");
    }

    @Test
    public void anUnreachableSutIsOneMessageWithTheRequest() {
        sut.stop(0);

        assertThatThrownBy(() -> users.get(7))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Cannot reach the SUT")
                .hasMessageContaining("GET /api/users/7");
    }

    @Test
    public void aBugInTheCallersOwnCodeIsNotBlamedOnTheSut() {
        ApiTransport transport = new ApiTransport(
                new ApiSettings(URI.create("http://localhost:" + sut.getAddress().getPort()), Duration.ofSeconds(5)), List.of());

        assertThatThrownBy(() -> transport.send(api -> {
            throw new IllegalArgumentException("a bug in the test");
        }))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("a bug in the test");
    }

    @Test
    public void responseHeadersAreFoundIgnoringCaseWithEveryValueOfARepeatedOne() {
        answer.set(new Answer(200, ADA));

        ApiResponse response = transport.send(api -> api.users().getUser().idPath(7));

        assertThat(response.header("CONTENT-TYPE")).contains("application/json");
        assertThat(response.headers("link")).containsExactly(
                "</api/users?page=2>; rel=\"next\"", "</api/users?page=0>; rel=\"prev\"");
        assertThat(response.header("ETag")).isEmpty();
        assertThat(response.headers("ETag")).isEmpty();
    }
}
