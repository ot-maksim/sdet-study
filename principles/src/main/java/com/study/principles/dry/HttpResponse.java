package com.study.principles.dry;

import java.util.Map;

/** What the fake server answers: a status and a parsed JSON body. */
public record HttpResponse(int status, Map<String, Object> json) {

    public HttpResponse {
        json = Map.copyOf(json);
    }

    @Override
    public String toString() {
        return status + " " + new java.util.TreeMap<>(json);
    }
}
