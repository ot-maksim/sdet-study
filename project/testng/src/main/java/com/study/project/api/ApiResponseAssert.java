package com.study.project.api;

import java.util.List;
import org.assertj.core.api.AbstractAssert;

/**
 * AssertJ assertions for negative API tests and contract tests. Failures show the whole exchange, not just two numbers:
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

    /**
     * The response is as the SUT's contract says (ADR 0015). Assert the status first: it picks which response of the
     * operation the body is checked against, so a contract test without it may check the wrong branch.
     */
    public ApiResponseAssert conformsTo(Contract contract) {
        isNotNull();
        List<String> problems = contract.violations(actual);
        if (!problems.isEmpty()) {
            failWithMessage("Expected the response to keep the SUT's contract (openapi.yaml), but:%n%s%n%s",
                    String.join("\n", problems.stream().map(problem -> "  - " + problem).toList()), actual);
        }
        return this;
    }
}
