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
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The contract check on a fake SUT that answers what each test tells it to (no Docker): a response that drifts from
 * {@code openapi.yaml} fails the call with the report and the exchange; a request that breaks the contract on
 * purpose (a negative test) does not.
 */
class ContractFilterTest {

    private static final String ADA = """
            {"id":7,"name":"Ada","email":"ada@example.test","age":30,"country":"DE","createdAt":"2026-11-22T10:15:30.123456Z"}""";

    private final AtomicReference<Answer> answer = new AtomicReference<>();
    private HttpServer sut;
    private UserApi users;

    private record Answer(int status, String body) {
    }

    @BeforeEach
    void startFakeSut() throws IOException {
        sut = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        sut.createContext("/", exchange -> {
            Answer next = answer.get();
            byte[] body = next.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("Location", "/api/users/7");
            exchange.sendResponseHeaders(next.status(), next.body().isEmpty() ? -1 : body.length);
            if (body.length > 0) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        sut.start();
        ApiSettings settings = new ApiSettings(URI.create("http://localhost:" + sut.getAddress().getPort()), Duration.ofSeconds(5));
        users = new UserApi(new ApiTransport(settings, List.of(ContractFilter.fromClasspath("contract/openapi.yaml"))));
    }

    @AfterEach
    void stopFakeSut() {
        sut.stop(0);
    }

    @Test
    void aResponseAsTheContractSaysIsMappedToTheProjectsRecord() {
        answer.set(new Answer(200, ADA));

        assertThat(users.get(7)).isEqualTo(new User(7, "Ada", "ada@example.test", 30, "DE",
                Instant.parse("2026-11-22T10:15:30.123456Z")));
    }

    @Test
    void aMissingRequiredFieldFailsTheCallWithTheReportAndTheExchange() {
        answer.set(new Answer(200, ADA.replace(",\"createdAt\":\"2026-11-22T10:15:30.123456Z\"", "")));

        assertThatThrownBy(() -> users.get(7))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("breaks the SUT's contract")
                .hasMessageContaining("createdAt")
                .hasMessageContaining("GET /api/users/7");
    }

    @Test
    void aNewErrorCodeIsAContractChange() {
        answer.set(new Answer(400, "{\"error\":\"age_negative\",\"message\":\"Age must not be negative\"}"));

        assertThatThrownBy(() -> users.tryCreate(new NewUser("Ada", "ada@example.test", -1, "DE")))
                .hasMessageContaining("breaks the SUT's contract")
                .hasMessageContaining("age_negative");
    }

    @Test
    void aStatusTheOperationDoesNotDeclareIsAContractChange() {
        answer.set(new Answer(200, ADA));

        assertThatThrownBy(() -> users.tryCreate(new NewUser("Ada", "ada@example.test", 30, "DE")))
                .hasMessageContaining("breaks the SUT's contract")
                .hasMessageContaining("200");
    }

    @Test
    void aRequestTheContractForbidsIsSentForTheSutToReject() {
        answer.set(new Answer(400, "{\"error\":\"age_too_low\",\"message\":\"Age must be at least 18\"}"));

        ApiResponse response = users.tryCreate(new NewUser("Kid", "kid@example.test", 17, null));

        assertThat(response.isError(400, "age_too_low")).isTrue();
        assertThat(response.toString()).contains("POST /api/users").contains("\"age\":17").doesNotContain("country");
    }

    @Test
    void anUnreachableSutIsOneMessageWithTheRequest() {
        sut.stop(0);

        assertThatThrownBy(() -> users.get(7))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Cannot reach the SUT")
                .hasMessageContaining("GET /api/users/7");
    }

    @Test
    void aBugInTheCallersOwnCodeIsNotBlamedOnTheSut() {
        ApiTransport transport = new ApiTransport(
                new ApiSettings(URI.create("http://localhost:" + sut.getAddress().getPort()), Duration.ofSeconds(5)), List.of());

        assertThatThrownBy(() -> transport.send(api -> {
            throw new IllegalArgumentException("a bug in the test");
        }))
                .isExactlyInstanceOf(IllegalArgumentException.class)
                .hasMessage("a bug in the test");
    }
}
