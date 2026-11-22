package com.study.project.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Records one call: the request as sent and the response as received, detached from RestAssured. One instance per
 * call ({@link ApiTransport#send}), used by the calling thread only.
 */
final class Exchange implements Filter {

    private final ObjectMapper json;
    private String method = "?";
    private String path = "?";
    private String requestBody;
    private ApiResponse response;

    Exchange(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification responseSpec, FilterContext context) {
        URI uri = URI.create(request.getURI());
        method = request.getMethod();
        path = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        requestBody = text(request.getBody());
        Response received = context.next(request, responseSpec);
        response = new ApiResponse(method, path, requestBody, received.statusCode(), received.asString(),
                received.getHeader("Location"), json);
        return received;
    }

    /** {@code GET /api/users/7}, also when nothing came back. */
    String request() {
        return method + " " + path;
    }

    ApiResponse response() {
        if (response == null) {
            throw new IllegalStateException("No response recorded for " + request());
        }
        return response;
    }

    private static String text(Object body) {
        if (body == null) {
            return null;
        }
        return body instanceof byte[] bytes ? new String(bytes, StandardCharsets.UTF_8) : body.toString();
    }
}
