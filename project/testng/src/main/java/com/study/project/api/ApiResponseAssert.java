package com.study.project.api;

import org.assertj.core.api.AbstractAssert;

/**
 * AssertJ assertions for negative API tests. Failures show the whole exchange, not just two numbers:
 * <pre>
 * Expected 400 age_too_low but got:
 *   POST /api/users {"name":"Kid",...,"age":18,...}
 *   -> 201 {"id":7,...}
 * </pre>
 */
public final class ApiResponseAssert extends AbstractAssert<ApiResponseAssert, ApiResponse> {

    private ApiResponseAssert(ApiResponse actual) {
        super(actual, ApiResponseAssert.class);
    }

    public static ApiResponseAssert assertThat(ApiResponse actual) {
        return new ApiResponseAssert(actual);
    }

    public ApiResponseAssert hasStatus(int expected) {
        isNotNull();
        if (actual.status() != expected) {
            failWithMessage("Expected %s but got:%n%s", expected, actual);
        }
        return this;
    }

    /** Status and the machine-readable code; the human message is deliberately not compared. */
    public ApiResponseAssert isRejectedWith(int status, String code) {
        isNotNull();
        if (!actual.isError(status, code)) {
            failWithMessage("Expected %s %s but got:%n%s", status, code, actual);
        }
        return this;
    }
}
