package com.study.principles.deep;

import java.util.Map;

/** What would go over the wire. {@code body} is {@code null} for requests without one. */
public record HttpRequest(String method, String path, Map<String, String> headers, String body) {

    public HttpRequest {
        headers = Map.copyOf(headers);
    }

    public String header(String name) {
        return headers.get(name);
    }

    @Override
    public String toString() {
        return method + " " + path + " " + headers + (body == null ? "" : " " + body);
    }
}
