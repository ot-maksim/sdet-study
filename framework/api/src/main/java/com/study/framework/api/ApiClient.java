package com.study.framework.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.http.Method;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.Map;

/**
 * Transport: base URL, timeouts, JSON. Immutable and shared by the whole run — every call starts a new
 * request from the spec ({@code given().spec(spec)}), nothing is mutated, so parallel tests are safe
 * (unlike a shared mutable {@code RequestSpecification}, see principles/parallel). No RestAssured statics
 * ({@code RestAssured.baseURI = ...}) are touched.
 *
 * <p>Resource clients ({@link com.study.framework.api.users.UserApi}) build on it; it knows no paths.
 */
public final class ApiClient {

    private final ApiSettings settings;
    private final RequestSpecification spec;
    private final ObjectMapper json;

    public ApiClient(ApiSettings settings) {
        this.settings = settings;
        this.json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                // Tolerant reader: a new field in a response must not break unrelated tests
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        int timeoutMs = Math.toIntExact(settings.timeout().toMillis());
        this.spec = new RequestSpecBuilder()
                .setBaseUri(settings.baseUrl().toString())
                .setContentType(ContentType.JSON)
                .setAccept(ContentType.JSON)
                .setConfig(RestAssuredConfig.config().httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", timeoutMs)
                        .setParam("http.socket.timeout", timeoutMs)))
                .build();
    }

    public ApiResponse get(String path) {
        return send(Method.GET, path, Map.of(), null);
    }

    public ApiResponse get(String path, Map<String, ?> query) {
        return send(Method.GET, path, query, null);
    }

    public ApiResponse post(String path, Object body) {
        return send(Method.POST, path, Map.of(), toJson(body));
    }

    /** A body sent as is: malformed JSON, missing fields — what a DTO cannot express. */
    public ApiResponse postRaw(String path, String body) {
        return send(Method.POST, path, Map.of(), body);
    }

    public ApiResponse delete(String path) {
        return send(Method.DELETE, path, Map.of(), null);
    }

    private ApiResponse send(Method method, String path, Map<String, ?> query, String body) {
        RequestSpecification request = RestAssured.given().spec(spec).queryParams(query);
        if (body != null) {
            request.body(body);
        }
        String shownPath = query.isEmpty() ? path : path + "?" + query;
        Response response;
        try {
            response = request.request(method, path);
        } catch (Exception e) {   // RestAssured rethrows checked IOExceptions (ConnectException) undeclared
            throw new ApiException("Cannot reach the SUT at " + settings.baseUrl() + " (" + method + " " + shownPath + "): "
                    + e + ". Is the stand up? docker compose up -d --wait", e);
        }
        return new ApiResponse(method.name(), shownPath, body, response.statusCode(), response.asString(),
                response.getHeader("Location"), json);
    }

    private String toJson(Object body) {
        try {
            return json.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Cannot serialize " + body, e);
        }
    }
}
