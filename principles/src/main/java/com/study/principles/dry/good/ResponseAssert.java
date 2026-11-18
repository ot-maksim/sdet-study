package com.study.principles.dry.good;

import com.study.principles.dry.HttpResponse;
import org.assertj.core.api.AbstractAssert;

import java.util.List;
import java.util.Objects;

/**
 * A custom AssertJ assertion: DRY for <em>how</em> to check, while each test still says <em>what</em> it expects.
 * Every failure names the field, the expectation and the whole response.
 */
public final class ResponseAssert extends AbstractAssert<ResponseAssert, HttpResponse> {

    private ResponseAssert(HttpResponse actual) {
        super(actual, ResponseAssert.class);
    }

    public static ResponseAssert assertThat(HttpResponse actual) {
        return new ResponseAssert(actual);
    }

    public ResponseAssert isCreated() {
        isNotNull();
        if (actual.status() != 201) {
            failWithMessage("Expected the user to be created (201), but the response was%n  %s", actual);
        }
        return this;
    }

    public ResponseAssert isRejectedWith(String error) {
        isNotNull();
        if (actual.status() != 400 || !error.equals(actual.json().get("error"))) {
            failWithMessage("Expected 400 <%s>, but the response was%n  %s", error, actual);
        }
        return this;
    }

    public ResponseAssert hasValidationError(String error) {
        isNotNull();
        if (actual.status() != 422 || !(actual.json().get("errors") instanceof List<?> errors && errors.contains(error))) {
            failWithMessage("Expected 422 with <%s>, but the response was%n  %s", error, actual);
        }
        return this;
    }

    public ResponseAssert has(String field, Object expected) {
        isNotNull();
        Object value = actual.json().get(field);
        if (!Objects.equals(value, expected)) {
            failWithMessage("Expected <%s> to be <%s> but was <%s> in%n  %s", field, expected, value, actual);
        }
        return this;
    }
}
