package com.study.sut.web;

/** Error body of the REST API: {@code {"error": "age_too_low", "message": "..."}}. */
public record ApiError(String error, String message) {
}
