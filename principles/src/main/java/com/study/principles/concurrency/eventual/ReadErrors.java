package com.study.principles.concurrency.eventual;

/** Errors of the read side, as an HTTP client would raise them. */
public final class ReadErrors {

    private ReadErrors() {
    }

    /** 404: not projected <b>yet</b>. The one error a wait for eventual consistency should expect. */
    public static final class NotFound extends RuntimeException {
        public NotFound(String id) {
            super("404 Not Found: " + id);
        }
    }

    /** 401: a real problem. Waiting will not fix it; the test should say so at once. */
    public static final class Unauthorized extends RuntimeException {
        public Unauthorized() {
            super("401 Unauthorized: token expired");
        }
    }
}
