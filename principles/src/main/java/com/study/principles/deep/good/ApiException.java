package com.study.principles.deep.good;

import com.study.principles.deep.HttpResponse;

/**
 * The error translated at the boundary: says which call, what was expected and what came back.
 * Compare with {@code NumberFormatException: Cannot parse null string} from the bad design.
 */
public class ApiException extends RuntimeException {

    private final transient HttpResponse response;

    ApiException(String call, int expectedStatus, HttpResponse response) {
        super(call + ": expected " + expectedStatus + " but was " + response.status() + ", body: " + response.body());
        this.response = response;
    }

    public HttpResponse response() {
        return response;
    }

    static HttpResponse expect(String call, int expectedStatus, HttpResponse response) {
        if (response.status() != expectedStatus) throw new ApiException(call, expectedStatus, response);
        return response;
    }
}
