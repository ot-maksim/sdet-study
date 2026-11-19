package com.study.review.good.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;

/**
 * One exchange: what was asked and what came back. Failures quote both, so a broken precondition fails at the
 * call with "POST /api/users → 400 age_too_low", not as an NPE three steps later.
 */
public record ApiResponse(String request, int status, String body) {

    static final ObjectMapper JSON = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    public ApiResponse expect(int expected) {
        if (status != expected) {
            throw new ApiException(request + " → expected " + expected + ", got " + status + ": " + body);
        }
        return this;
    }

    public boolean isError(int status, String code) {
        return this.status == status && code.equals(errorCode());
    }

    /** The {@code error} field of an error body, or null. */
    public String errorCode() {
        try {
            JsonNode error = JSON.readTree(body).get("error");
            return error == null ? null : error.asText();
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    public <T> T as(Class<T> type) {
        try {
            return JSON.readValue(body, type);
        } catch (JsonProcessingException e) {
            throw new ApiException(request + " → " + status + ", body is not a " + type.getSimpleName() + ": " + body, e);
        }
    }

    public <T> List<T> asListOf(Class<T> type) {
        try {
            return JSON.readValue(body, JSON.getTypeFactory().constructCollectionType(List.class, type));
        } catch (JsonProcessingException e) {
            throw new ApiException(request + " → " + status + ", body is not a list of " + type.getSimpleName() + ": " + body, e);
        }
    }
}
