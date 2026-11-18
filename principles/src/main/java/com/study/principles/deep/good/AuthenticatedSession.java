package com.study.principles.deep.good;

import com.study.principles.deep.HttpRequest;
import com.study.principles.deep.HttpResponse;
import com.study.principles.deep.Transport;

import java.util.Map;

/**
 * Proof of a successful login. The constructor is package-private: the only way to get one is
 * {@link AuthApi#login}. Every API that needs auth takes a session in its constructor, so
 * "login before createUser" is no longer a rule to remember — the wrong order does not compile.
 * <p>
 * Each request is built from scratch; nothing is carried over from the previous call.
 */
public final class AuthenticatedSession {

    private final Transport transport;
    private final String user;
    private final String token;

    AuthenticatedSession(Transport transport, String user, String token) {
        this.transport = transport;
        this.user = user;
        this.token = token;
    }

    public String user() {
        return user;
    }

    HttpResponse post(String path, String json) {
        return transport.send(new HttpRequest("POST", path,
                Map.of("Authorization", "Bearer " + token, "Content-Type", "application/json"), json));
    }

    HttpResponse get(String path) {
        return transport.send(new HttpRequest("GET", path, Map.of("Authorization", "Bearer " + token), null));
    }

    @Override
    public String toString() {
        return "AuthenticatedSession[user=" + user + "]";   // never the token
    }
}
