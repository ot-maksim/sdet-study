package com.study.principles.deep.bad;

import com.study.principles.deep.HttpRequest;
import com.study.principles.deep.HttpResponse;
import com.study.principles.deep.Transport;

import java.util.HashMap;
import java.util.Map;

/**
 * SMELL: shallow module. Its interface is as big as what it does — every method is one line of HTTP
 * renamed, so every caller still has to know HTTP: which headers, which order, how to read the result.
 * <p>
 * SMELL: temporal coupling. The object is a mutable request-in-progress:
 * <ul>
 *   <li>{@code setHeader}/{@code setBody} must happen before {@code post} — nothing enforces it;</li>
 *   <li>{@code getStatus}/{@code getBody} are meaningful only after a send — before it they return 0/{@code null};</li>
 *   <li>headers and body are never reset, so they silently ride along with the next request.</li>
 * </ul>
 */
public class HttpHelper {

    private final Transport transport;
    private final Map<String, String> headers = new HashMap<>();
    private String body;
    private HttpResponse response;

    public HttpHelper(Transport transport) {
        this.transport = transport;
    }

    public void setHeader(String name, String value) {
        headers.put(name, value);
    }

    public void setBody(String body) {
        this.body = body;
    }

    public void post(String path) {
        response = transport.send(new HttpRequest("POST", path, headers, body));
    }

    public void get(String path) {
        response = transport.send(new HttpRequest("GET", path, headers, body));   // yes, with the old body
    }

    public int getStatus() {
        return response == null ? 0 : response.status();
    }

    public String getBody() {
        return response == null ? null : response.body();
    }
}
