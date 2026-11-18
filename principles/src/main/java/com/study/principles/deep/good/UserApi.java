package com.study.principles.deep.good;

import com.study.principles.deep.HttpResponse;
import com.study.principles.deep.Json;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Deep module: a small interface in the test's terms over everything a call to {@code /api/users} needs —
 * auth and content-type headers, escaping JSON, the expected status, translating errors, parsing the id.
 * <p>
 * Two levels on purpose:
 * <ul>
 *   <li>{@link #create} — for the 95% of tests where creating a user is a precondition; fails loudly;</li>
 *   <li>{@link #tryCreate} — for negative tests of the endpoint itself, where the status IS the assertion.
 *       Hiding it would make the module "too deep": tests would bypass it to reach the status.</li>
 * </ul>
 */
public final class UserApi {

    private static final String USERS = "/api/users";

    private final AuthenticatedSession session;

    public UserApi(AuthenticatedSession session) {
        this.session = Objects.requireNonNull(session, "session");
    }

    public User create(NewUser user) {
        HttpResponse response = ApiException.expect("POST " + USERS + " as " + session.user(), 201, tryCreate(user));
        return toUser(response.body());
    }

    public HttpResponse tryCreate(NewUser user) {
        return session.post(USERS, toJson(user));
    }

    public User get(long id) {
        HttpResponse response = ApiException.expect("GET " + USERS + "/" + id, 200, session.get(USERS + "/" + id));
        return toUser(response.body());
    }

    private static String toJson(NewUser user) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("name", user.name());
        json.put("email", user.email());
        json.put("role", user.role().name());
        return Json.object(json);
    }

    private static User toUser(String body) {
        Map<String, String> json = Json.parse(body);
        return new User(Long.parseLong(json.get("id")), json.get("name"), json.get("email"),
                Role.valueOf(json.get("role")));
    }
}
