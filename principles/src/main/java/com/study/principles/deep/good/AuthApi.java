package com.study.principles.deep.good;

import com.study.principles.deep.HttpRequest;
import com.study.principles.deep.HttpResponse;
import com.study.principles.deep.Json;
import com.study.principles.deep.Transport;

import java.util.LinkedHashMap;
import java.util.Map;

/** The only producer of {@link AuthenticatedSession}. */
public final class AuthApi {

    private final Transport transport;

    public AuthApi(Transport transport) {
        this.transport = transport;
    }

    public AuthenticatedSession login(String user, String password) {
        Map<String, String> credentials = new LinkedHashMap<>();
        credentials.put("user", user);
        credentials.put("password", password);
        HttpResponse response = transport.send(new HttpRequest("POST", "/login",
                Map.of("Content-Type", "application/json"), Json.object(credentials)));
        ApiException.expect("POST /login as " + user, 200, response);
        return new AuthenticatedSession(transport, user, Json.parse(response.body()).get("token"));
    }
}
