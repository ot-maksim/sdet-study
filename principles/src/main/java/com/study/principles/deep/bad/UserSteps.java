package com.study.principles.deep.bad;

import com.study.principles.deep.FakeUsersBackend;
import com.study.principles.deep.Json;

/**
 * SMELL: a layer without depth. It looks like an abstraction ("steps"), but:
 * <ul>
 *   <li>{@code login} must be called before {@code createUser} (temporal coupling) — and it does so by
 *       mutating the shared {@link HttpHelper}, so the token outlives the "test" that logged in;</li>
 *   <li>JSON is built with {@code String.format} — no escaping, so a name with a quote breaks the request;</li>
 *   <li>the status is never checked: on any error {@code createUser} fails with {@link NumberFormatException}
 *       from parsing a missing {@code id}, far from the cause;</li>
 *   <li>{@code getStatus}/{@code getResponseBody} are pass-through methods: they add interface, not behaviour,
 *       and exist only because the error was not translated.</li>
 * </ul>
 */
public class UserSteps {

    private final HttpHelper http;

    public UserSteps(HttpHelper http) {
        this.http = http;
    }

    public void login(String user, String password) {
        http.setHeader("Content-Type", "application/json");
        http.setBody(String.format("{\"user\":\"%s\",\"password\":\"%s\"}", user, password));
        http.post("/login");
        http.setHeader("Authorization", "Bearer " + Json.field(http.getBody(), "token"));
    }

    public void loginAsAdmin() {
        login(FakeUsersBackend.ADMIN, FakeUsersBackend.ADMIN_PASSWORD);
    }

    /** @return id of the created user */
    public int createUser(String name, String email, String role) {
        http.setBody(String.format("{\"name\":\"%s\",\"email\":\"%s\",\"role\":\"%s\"}", name, email, role));
        http.post("/api/users");
        return Integer.parseInt(Json.field(http.getBody(), "id"));
    }

    public int getStatus() {
        return http.getStatus();
    }

    public String getResponseBody() {
        return http.getBody();
    }
}
