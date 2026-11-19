package com.study.framework.api;

/** The SUT's error body: {@code {"error": "age_too_low", "message": "..."}}. {@code code} is the contract, {@code message} is not. */
public record ApiError(String code, String message) {
}
