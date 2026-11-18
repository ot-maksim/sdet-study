package com.study.principles.dry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The system under test behind "HTTP": {@code POST} users and companies. No sockets, just the rules.
 *
 * <p>Users: 400 with a single {@code error} code. Companies were written by another team: 422 with a list of
 * {@code errors}. The two look alike at a glance — which is how they end up behind one helper with a flag.
 */
public final class FakeServer {

    private final Release release;
    private final AtomicLong ids = new AtomicLong();

    public FakeServer(Release release) {
        this.release = release;
    }

    public HttpResponse post(String path, Map<String, ?> body) {
        if (path.equals(release.usersPath())) {
            return createUser(body);
        }
        if (path.equals(Release.COMPANIES_PATH)) {
            return createCompany(body);
        }
        return new HttpResponse(404, Map.of("error", "not_found", "path", path));
    }

    private HttpResponse createUser(Map<String, ?> body) {
        String name = (String) body.get("name");
        String email = (String) body.get("email");
        Integer age = (Integer) body.get("age");
        if (name == null || name.isBlank()) {
            return rejected("name_required");
        }
        if (email == null || !email.contains("@")) {
            return rejected("email_invalid");
        }
        if (age == null) {
            return rejected("age_required");
        }
        if (age < release.minAge()) {
            return rejected("age_too_low");
        }
        Object country = body.get("country");
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", ids.incrementAndGet());
        user.put("name", name);
        user.put("email", email);
        user.put("age", age);
        user.put("country", release.keepsCountry() && country != null ? country : "US");
        user.put("role", "USER");
        user.put("status", "ACTIVE");
        return new HttpResponse(201, user);
    }

    private HttpResponse createCompany(Map<String, ?> body) {
        Object name = body.get("name");
        Object vatId = body.get("vatId");
        List<String> errors = new java.util.ArrayList<>();
        if (name == null || name.toString().isBlank()) {
            errors.add("name: required");
        }
        if (vatId == null) {
            errors.add("vatId: required");
        }
        if (!errors.isEmpty()) {
            return new HttpResponse(422, Map.of("errors", List.copyOf(errors)));
        }
        return new HttpResponse(201, Map.of("id", ids.incrementAndGet(), "name", name, "vatId", vatId));
    }

    private static HttpResponse rejected(String code) {
        return new HttpResponse(400, Map.of("error", code));
    }
}
