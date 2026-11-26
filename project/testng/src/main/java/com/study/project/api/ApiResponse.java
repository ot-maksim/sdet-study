package com.study.project.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A response with the request that caused it, detached from RestAssured: tests and steps never see
 * {@code io.restassured.*}, so the transport can be replaced without touching them.
 *
 * <p>Headers are looked up by name ignoring case, as HTTP says ({@code etag} finds {@code ETag}). The business meaning
 * of a header (a version, a page total) belongs to the resource class that turns the response into a record
 * ({@code UserApi}), not to tests.
 */
public final class ApiResponse {

    private final String method;
    private final String path;
    private final String requestBody;
    private final int status;
    private final String body;
    private final List<Map.Entry<String, String>> headers;
    private final ObjectMapper json;

    ApiResponse(String method, String path, String requestBody, int status, String body, List<Map.Entry<String, String>> headers,
            ObjectMapper json) {
        this.method = method;
        this.path = path;
        this.requestBody = requestBody;
        this.status = status;
        this.body = body;
        this.headers = List.copyOf(headers);
        this.json = json;
    }

    public int status() {
        return status;
    }

    /** {@code GET}; for the contract check. */
    String method() {
        return method;
    }

    /** The raw path with the query, as sent; for the contract check. */
    String path() {
        return path;
    }

    /** Every header in the order received; for the contract check. */
    List<Map.Entry<String, String>> allHeaders() {
        return headers;
    }

    public String body() {
        return body;
    }

    /** The first value of the header, empty if the response has none. */
    public Optional<String> header(String name) {
        return headers(name).stream().findFirst();
    }

    /** All values of a header that may repeat ({@code Set-Cookie}, {@code Link}), in the order received. */
    public List<String> headers(String name) {
        String wanted = name.toLowerCase(Locale.ROOT);
        return headers.stream()
                .filter(header -> header.getKey().toLowerCase(Locale.ROOT).equals(wanted))
                .map(Map.Entry::getValue)
                .toList();
    }

    public Optional<String> location() {
        return header("Location");
    }

    /** Fails with the whole exchange in the message if the status is not the expected one. */
    public ApiResponse expect(int expectedStatus) {
        if (status != expectedStatus) {
            throw new ApiException("Expected " + expectedStatus + " but got:\n" + this);
        }
        return this;
    }

    public <T> T as(Class<T> type) {
        try {
            return json.readValue(body, type);
        } catch (JsonProcessingException e) {
            throw new ApiException("Cannot read " + type.getSimpleName() + " from:\n" + this, e);
        }
    }

    public <T> List<T> asListOf(Class<T> type) {
        try {
            return json.readValue(body, json.getTypeFactory().constructCollectionType(List.class, type));
        } catch (JsonProcessingException e) {
            throw new ApiException("Cannot read a list of " + type.getSimpleName() + " from:\n" + this, e);
        }
    }

    /** The error body; fails if the response is not one. */
    public ApiError error() {
        try {
            var node = json.readTree(body);
            if (node == null || !node.hasNonNull("error")) {
                throw new ApiException("Not an error body:\n" + this);
            }
            return new ApiError(node.get("error").asText(), node.path("message").asText(null));
        } catch (JsonProcessingException e) {
            throw new ApiException("Not an error body:\n" + this, e);
        }
    }

    public boolean isError(int expectedStatus, String code) {
        if (status != expectedStatus) {
            return false;
        }
        try {
            return code.equals(error().code());
        } catch (ApiException notAnErrorBody) {
            return false;
        }
    }

    /** {@code POST /api/users {"name":...} -> 400 {"error":"age_too_low",...}} */
    @Override
    public String toString() {
        return "  " + method + " " + path + (requestBody == null ? "" : " " + requestBody)
                + "\n  -> " + status + (body == null || body.isEmpty() ? "" : " " + body);
    }
}
