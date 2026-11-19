package com.study.review.good.api;

/** An unexpected answer of the API; the message carries the request and the whole response. */
public final class ApiException extends RuntimeException {

    public ApiException(String message) {
        super(message);
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
