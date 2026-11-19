package com.study.framework.api.users;

import com.study.framework.api.ApiClient;
import com.study.framework.api.ApiException;
import com.study.framework.api.ApiResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The users resource as intentions (a deep interface, principles/deep): callers say "create this user",
 * not "POST this JSON and check 201". Paths, statuses and parsing live here — one place to change
 * when the API changes.
 *
 * <p>Two kinds of methods: the happy path returns domain DTOs and fails loudly with the whole exchange
 * ({@link #create}); {@code try*} methods return the raw {@link ApiResponse} for negative tests.
 */
public final class UserApi {

    static final String USERS = "/api/users";

    private final ApiClient client;

    public UserApi(ApiClient client) {
        this.client = client;
    }

    public User create(NewUser user) {
        return tryCreate(user).expect(201).as(User.class);
    }

    public ApiResponse tryCreate(NewUser user) {
        return client.post(USERS, user);
    }

    /** For bodies a DTO cannot express: malformed JSON, wrong types. */
    public ApiResponse tryCreateRaw(String json) {
        return client.postRaw(USERS, json);
    }

    public User get(long id) {
        return client.get(USERS + "/" + id).expect(200).as(User.class);
    }

    /**
     * Empty only for the SUT's own "no such user" ({@code 404 user_not_found}). Any other 404 — a wrong path,
     * a proxy page — is an error, not "absent" (principles/concurrency/eventual: name your "not yet").
     */
    public Optional<User> find(long id) {
        ApiResponse response = client.get(USERS + "/" + id);
        if (response.isError(404, "user_not_found")) {
            return Optional.empty();
        }
        return Optional.of(response.expect(200).as(User.class));
    }

    public Optional<User> findByEmail(String email) {
        List<User> found = client.get(USERS, Map.of("email", email)).expect(200).asListOf(User.class);
        if (found.size() > 1) {
            throw new ApiException("Email must be unique, found " + found.size() + " users for " + email + ": " + found);
        }
        return found.stream().findFirst();
    }

    public List<User> all() {
        return client.get(USERS).expect(200).asListOf(User.class);
    }

    public void delete(long id) {
        client.delete(USERS + "/" + id).expect(204);
    }

    /** Idempotent, for cleanup: a user the test already deleted is not a cleanup failure. */
    public boolean deleteIfExists(long id) {
        ApiResponse response = client.delete(USERS + "/" + id);
        if (response.isError(404, "user_not_found")) {
            return false;
        }
        response.expect(204);
        return true;
    }
}
