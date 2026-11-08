package com.study.principles.records.misfit.bad;

import java.util.List;

/**
 * BAD: a service client written as a record "because it is shorter".
 *
 * <h2>Why a record does not fit</h2>
 * A record is a <i>transparent carrier</i>: its public API is its state. A client is the opposite — it has
 * behaviour and internals that must stay hidden. Writing it as a record causes:
 * <ul>
 *   <li><b>No encapsulation.</b> Every component gets a public accessor: {@code token()} hands the secret to
 *       any caller, and {@code toString} prints it in every log line.</li>
 *   <li><b>Mutable state has to be smuggled in as a component.</b> A record cannot declare extra instance
 *       fields, so the request history becomes a {@code List} component — public, clearable by anyone.</li>
 *   <li><b>Meaningless value equality.</b> Two clients are "equal" when their fields are, and the hash
 *       changes after every request, so a client stored in a {@code HashSet} or used as a {@code Map}
 *       key becomes unreachable (the {@code invariants.hashkey} trap).</li>
 * </ul>
 * The same applies to page objects, driver sessions, DB connections and other objects with a lifecycle.
 *
 * @param baseUrl      SUT base URL
 * @param token        bearer token — publicly readable through {@code token()}
 * @param sentRequests request history — must be mutable, therefore exposed and part of {@code equals}
 * @see com.study.principles.records.misfit.good.UsersApiClient the class version
 */
public record UsersApiClient(String baseUrl, String token, List<String> sentRequests) {

    /** Pretends to send {@code GET /users/{id}}: records and returns the request line. */
    public String getUser(long id) {
        String request = "GET " + baseUrl + "/users/" + id;
        sentRequests.add(request);
        return request;
    }
}
