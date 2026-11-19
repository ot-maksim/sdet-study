package com.study.review.good.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@code /api/users} as intentions. Immutable, with its own request specification: nothing is read from or
 * written to RestAssured's static fields, so two clients for two stands can live in one JVM, in parallel.
 *
 * <p>{@link #create} is the precondition path: it returns a {@link User} or fails with the exchange.
 * {@link #tryCreate} is for negative tests: it returns whatever came back.
 */
public final class UsersClient {

    private static final String USERS = "/api/users";

    private final URI baseUrl;
    private final RequestSpecification spec;

    public UsersClient(URI baseUrl, Duration timeout) {
        this.baseUrl = baseUrl;
        int millis = Math.toIntExact(timeout.toMillis());
        this.spec = new RequestSpecBuilder()
                .setBaseUri(baseUrl.toString())
                .setContentType(ContentType.JSON)
                .setConfig(RestAssuredConfig.config().httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", millis)
                        .setParam("http.socket.timeout", millis)))
                .build();
    }

    public User create(NewUser user) {
        return tryCreate(user).expect(201).as(User.class);
    }

    public ApiResponse tryCreate(NewUser user) {
        return send("POST", USERS, user);
    }

    public Optional<User> find(long id) {
        ApiResponse response = send("GET", USERS + "/" + id, null);
        if (response.isError(404, "user_not_found")) {
            return Optional.empty();
        }
        return Optional.of(response.expect(200).as(User.class));
    }

    public Optional<User> findByEmail(String email) {
        return send("GET", USERS, null, Map.of("email", email)).expect(200).asListOf(User.class).stream().findFirst();
    }

    public List<User> all() {
        return send("GET", USERS, null).expect(200).asListOf(User.class);
    }

    /** Idempotent, for cleanup: a user the test already deleted is not a failure. */
    public void deleteIfExists(long id) {
        ApiResponse response = send("DELETE", USERS + "/" + id, null);
        if (!response.isError(404, "user_not_found")) {
            response.expect(204);
        }
    }

    private ApiResponse send(String method, String path, Object body) {
        return send(method, path, body, Map.of());
    }

    private ApiResponse send(String method, String path, Object body, Map<String, String> query) {
        String request = method + " " + baseUrl + path + (query.isEmpty() ? "" : "?" + query);
        RequestSpecification call = RestAssured.given(spec).queryParams(query);
        if (body != null) {
            try {
                call.body(ApiResponse.JSON.writeValueAsString(body));
            } catch (JsonProcessingException e) {
                throw new ApiException(request + ": cannot serialize " + body, e);
            }
        }
        Response response;
        try {
            response = call.request(method, path);
        } catch (RuntimeException e) {
            throw new ApiException(request + ": no response (" + e.getMessage() + ")", e);
        }
        return new ApiResponse(request + (body == null ? "" : " " + body), response.statusCode(), response.asString());
    }
}
