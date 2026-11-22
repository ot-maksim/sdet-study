package com.study.project.api.users;

import com.study.project.api.ApiException;
import com.study.project.api.ApiResponse;
import com.study.project.api.ApiTransport;
import java.util.List;
import java.util.Optional;

/**
 * The users resource as intentions (a deep interface): callers say "create this user", not "POST this JSON and
 * check 201". Statuses and parsing live here; paths and parameters come from the generated client, so a change of
 * the contract is a compile error in this class and nowhere else.
 *
 * <p>The generated models stop here (an anti-corruption layer): callers get the project's own records
 * ({@link User}, {@link NewUser}), immutable and with withers, so a regenerated model
 * (a new optional field, a renamed setter) changes this class only.
 *
 * <p>Two kinds of methods: the happy path returns records and fails loudly with the whole exchange ({@link #create});
 * {@code try*} methods return the {@link ApiResponse} for negative tests.
 */
public final class UserApi {

    private final ApiTransport api;

    public UserApi(ApiTransport api) {
        this.api = api;
    }

    public User create(NewUser user) {
        return toUser(tryCreate(user).expect(201));
    }

    public ApiResponse tryCreate(NewUser user) {
        return api.send(client -> client.users().createUser().body(toModel(user)));
    }

    public User get(long id) {
        return toUser(api.send(client -> client.users().getUser().idPath(id)).expect(200));
    }

    /**
     * Empty only for the SUT's own "no such user" ({@code 404 user_not_found}). Any other 404 — a wrong path,
     * a proxy page — is an error, not "absent".
     */
    public Optional<User> find(long id) {
        ApiResponse response = api.send(client -> client.users().getUser().idPath(id));
        if (response.isError(404, "user_not_found")) {
            return Optional.empty();
        }
        return Optional.of(toUser(response.expect(200)));
    }

    public Optional<User> findByEmail(String email) {
        List<User> found = toUsers(api.send(client -> client.users().listUsers().emailQuery(email)).expect(200));
        if (found.size() > 1) {
            throw new ApiException("Email must be unique, found " + found.size() + " users for " + email + ": " + found);
        }
        return found.stream().findFirst();
    }

    public List<User> all() {
        return toUsers(api.send(client -> client.users().listUsers()).expect(200));
    }

    public void delete(long id) {
        api.send(client -> client.users().deleteUser().idPath(id)).expect(204);
    }

    /** Idempotent, for cleanup: a user the test already deleted is not a cleanup failure. */
    public boolean deleteIfExists(long id) {
        ApiResponse response = api.send(client -> client.users().deleteUser().idPath(id));
        if (response.isError(404, "user_not_found")) {
            return false;
        }
        response.expect(204);
        return true;
    }

    /** {@code null} fields stay unset and are not sent: a missing value is a test case too. */
    private static com.study.project.api.generated.model.NewUser toModel(NewUser user) {
        return new com.study.project.api.generated.model.NewUser().name(user.name()).email(user.email()).age(user.age()).country(user.country());
    }

    private static User toUser(ApiResponse response) {
        return toUser(response.as(com.study.project.api.generated.model.User.class));
    }

    private static List<User> toUsers(ApiResponse response) {
        return response.asListOf(com.study.project.api.generated.model.User.class).stream().map(UserApi::toUser).toList();
    }

    private static User toUser(com.study.project.api.generated.model.User model) {
        return new User(model.getId(), model.getName(), model.getEmail(), model.getAge(), model.getCountry(),
                model.getCreatedAt().toInstant());
    }
}
