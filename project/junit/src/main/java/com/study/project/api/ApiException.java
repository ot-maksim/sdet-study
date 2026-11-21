package com.study.project.api;

/**
 * The SUT answered something the client did not expect, or could not be reached. The message carries the
 * request and the response, so a failed test explains itself without a debugger or a log hunt.
 */
public class ApiException extends RuntimeException {

    public ApiException(String message) {
        super(message);
    }

    public ApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
