package com.study.principles.deep;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * In-memory stand-in for the SUT's users service. Behaves like a strict real API:
 * <ul>
 *   <li>{@code POST /login} {@code {"user","password"}} → 200 {@code {"token"}} or 401</li>
 *   <li>{@code POST /api/users} → 401 without a valid bearer token, 415 without JSON content type,
 *       400 on malformed JSON or invalid email, 403 when a non-admin creates an ADMIN, 409 on a duplicate
 *       email, 201 with the created user otherwise</li>
 *   <li>{@code GET /api/users/{id}} → 401 / 404 / 200</li>
 * </ul>
 * Records every request so tests can see what actually went over the wire.
 */
public class FakeUsersBackend implements Transport {

    public static final String ADMIN = "admin";
    public static final String ADMIN_PASSWORD = "admin-pass";
    public static final String ALICE = "alice";
    public static final String ALICE_PASSWORD = "alice-pass";

    private static final Pattern USER_BY_ID = Pattern.compile("/api/users/(\\d+)");

    private record Account(String password, String role) {
    }

    private final Map<String, Account> accounts = Map.of(
            ADMIN, new Account(ADMIN_PASSWORD, "ADMIN"),
            ALICE, new Account(ALICE_PASSWORD, "USER"));
    private final Map<String, String> roleByToken = new LinkedHashMap<>();
    private final Map<Long, Map<String, String>> users = new LinkedHashMap<>();
    private final List<HttpRequest> requests = new ArrayList<>();
    private long nextId = 1;

    @Override
    public synchronized HttpResponse send(HttpRequest request) {
        requests.add(request);
        String route = request.method() + " " + request.path();
        if (route.equals("POST /login")) return login(request);
        if (route.equals("POST /api/users")) return createUser(request);
        Matcher byId = USER_BY_ID.matcher(request.path());
        if (request.method().equals("GET") && byId.matches()) return getUser(request, Long.parseLong(byId.group(1)));
        return error(404, "no route " + route);
    }

    public synchronized List<HttpRequest> requests() {
        return List.copyOf(requests);
    }

    private HttpResponse login(HttpRequest request) {
        Map<String, String> body;
        try {
            body = Json.parse(request.body());
        } catch (IllegalArgumentException e) {
            return error(400, "malformed json");
        }
        Account account = accounts.get(body.get("user"));
        if (account == null || !account.password().equals(body.get("password"))) {
            return error(401, "bad credentials");
        }
        String token = "t-" + (roleByToken.size() + 1) + "-" + body.get("user");
        roleByToken.put(token, account.role());
        return new HttpResponse(200, Json.object(Map.of("token", token)));
    }

    private HttpResponse createUser(HttpRequest request) {
        String callerRole = callerRole(request);
        if (callerRole == null) return error(401, "missing or invalid token");
        if (!"application/json".equals(request.header("Content-Type"))) return error(415, "expected application/json");
        Map<String, String> body;
        try {
            body = Json.parse(request.body());
        } catch (IllegalArgumentException e) {
            return error(400, "malformed json");
        }
        String name = body.get("name");
        String email = body.get("email");
        String role = body.getOrDefault("role", "USER");
        if (name == null || name.isBlank()) return error(400, "name is required");
        if (email == null || !email.matches("[^@\\s]+@[^@\\s]+")) return error(400, "invalid email");
        if (!role.equals("USER") && !role.equals("ADMIN")) return error(400, "unknown role " + role);
        if (role.equals("ADMIN") && !callerRole.equals("ADMIN")) return error(403, "only admins create admins");
        if (users.values().stream().anyMatch(u -> u.get("email").equals(email))) return error(409, "email taken");

        long id = nextId++;
        users.put(id, Map.of("name", name, "email", email, "role", role));
        return new HttpResponse(201, userJson(id));
    }

    private HttpResponse getUser(HttpRequest request, long id) {
        if (callerRole(request) == null) return error(401, "missing or invalid token");
        if (!users.containsKey(id)) return error(404, "user " + id + " not found");
        return new HttpResponse(200, userJson(id));
    }

    private String callerRole(HttpRequest request) {
        String auth = request.header("Authorization");
        if (auth == null || !auth.startsWith("Bearer ")) return null;
        return roleByToken.get(auth.substring("Bearer ".length()));
    }

    private String userJson(long id) {
        Map<String, String> u = users.get(id);
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("id", id);
        json.put("name", u.get("name"));
        json.put("email", u.get("email"));
        json.put("role", u.get("role"));
        return Json.object(json);
    }

    private static HttpResponse error(int status, String message) {
        return new HttpResponse(status, Json.object(Map.of("error", message)));
    }
}
