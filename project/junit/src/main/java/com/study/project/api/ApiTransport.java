package com.study.project.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.study.project.api.generated.ApiClient;
import com.study.project.api.generated.client.Oper;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.ObjectMapperConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.filter.Filter;
import java.util.List;
import java.util.function.Function;

/**
 * Transport over the client generated from the SUT's OpenAPI file: base URL, timeouts, JSON, filters (the contract
 * check, the report). Immutable and shared by the whole run; every call builds its request from scratch, no
 * RestAssured statics are touched.
 *
 * <p>What the generator gives: paths, methods, parameters and models that cannot drift from the contract — a moved
 * path is followed by regenerating, a renamed operation or parameter is a compile error in {@code UserApi}, not a
 * 404 at night. What it does not give, and this class adds: one
 * {@link ApiResponse} with the request and the response for every call, failures that explain themselves, and the
 * contract check of every response ({@link ContractFilter}).
 *
 * <pre>{@code
 * ApiResponse response = transport.send(api -> api.users().getUser().idPath(7));
 * }</pre>
 */
public final class ApiTransport {

    private final ApiSettings settings;
    private final List<Filter> filters;
    private final ObjectMapper json;

    /** @param filters run around every call in this order, the first outermost (the contract check, the report) */
    public ApiTransport(ApiSettings settings, List<Filter> filters) {
        this.settings = settings;
        this.filters = List.copyOf(filters);
        this.json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                // A null field of a request is not sent: a missing value is a test case of its own
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                // Tolerant reader: a new field in a response must not break unrelated tests
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /**
     * Sends one generated operation and returns the exchange whatever the status: a status is the caller's
     * decision ({@link ApiResponse#expect}). Fails only when there is no exchange to look at (the SUT is not
     * reachable) or the response breaks the contract.
     */
    public ApiResponse send(Function<ApiClient, ? extends Oper> operation) {
        Exchange exchange = new Exchange(json);
        ApiClient client = ApiClient.api(ApiClient.Config.apiConfig().reqSpecSupplier(() -> spec(exchange)));
        try {
            operation.apply(client).execute(response -> response);
        } catch (ContractFilter.Violation violation) {
            throw new ApiException("The response breaks the SUT's contract (openapi.yaml):\n"
                    + violation.getMessage() + "\n" + exchange.response(), violation);
        } catch (Exception e) {   // RestAssured rethrows checked IOExceptions (ConnectException) undeclared
            throw new ApiException("Cannot reach the SUT at " + settings.baseUrl() + " (" + exchange.request() + "): " + e
                    + ". Is the environment up?", e);
        }
        return exchange.response();
    }

    /** A fresh spec per call; the recorder is the innermost filter, so it sees what really went over the wire. */
    private RequestSpecBuilder spec(Exchange exchange) {
        int timeoutMs = Math.toIntExact(settings.timeout().toMillis());
        RequestSpecBuilder spec = new RequestSpecBuilder()
                .setBaseUri(settings.baseUrl().toString())
                .setConfig(RestAssuredConfig.config()
                        .objectMapperConfig(ObjectMapperConfig.objectMapperConfig().jackson2ObjectMapperFactory((type, charset) -> json))
                        .httpClient(HttpClientConfig.httpClientConfig()
                                .setParam("http.connection.timeout", timeoutMs)
                                .setParam("http.socket.timeout", timeoutMs)));
        filters.forEach(spec::addFilter);
        return spec.addFilter(exchange);
    }
}
