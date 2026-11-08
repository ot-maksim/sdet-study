package com.study.principles.records.misfit.good;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * GOOD: a plain final class — behaviour public, internals private.
 *
 * <h2>Design decisions</h2>
 * <ul>
 *   <li><b>The token never leaves the object</b>: no accessor, not in {@code toString}. It is used only to
 *       build the {@code Authorization} header inside {@link #getUser(long)}.</li>
 *   <li><b>Private mutable state is allowed</b> — the request history is an implementation detail; callers
 *       get a read-only snapshot.</li>
 *   <li><b>Identity equality</b> ({@code Object.equals}) — two clients are two connections, not two equal
 *       values. Safe as a map key, stable hash.</li>
 * </ul>
 * Records still appear here, but at the edges: the arguments and return values of such a client
 * ({@code UserResponse}, {@code Credentials}) are records.
 *
 * @see com.study.principles.records.misfit.bad.UsersApiClient the record version
 */
public final class UsersApiClient {
    private final String baseUrl;
    private final String token;
    private final List<String> sentRequests = new ArrayList<>();

    public UsersApiClient(String baseUrl, String token) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.token = Objects.requireNonNull(token, "token");
    }

    /** Pretends to send {@code GET /users/{id}}: records and returns the request line. */
    public String getUser(long id) {
        String request = "GET " + baseUrl + "/users/" + id;
        send(request, "Bearer " + token);
        sentRequests.add(request);
        return request;
    }

    private void send(String requestLine, String authorizationHeader) {
        // A real client would hand both to RestAssured here; the header never reaches logs or callers.
    }

    /** Read-only snapshot of what was sent; changing it does not affect the client. */
    public List<String> sentRequests() {
        return List.copyOf(sentRequests);
    }

    @Override
    public String toString() {
        return "UsersApiClient[baseUrl=" + baseUrl + ", requests=" + sentRequests.size() + "]";
    }
}
