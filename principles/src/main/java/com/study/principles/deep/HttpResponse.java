package com.study.principles.deep;

/** Status and raw JSON body, exactly as a low-level HTTP client would return them. */
public record HttpResponse(int status, String body) {

    @Override
    public String toString() {
        return status + " " + body;
    }
}
