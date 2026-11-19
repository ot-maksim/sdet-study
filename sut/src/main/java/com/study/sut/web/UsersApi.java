package com.study.sut.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.sut.users.NewUser;
import com.study.sut.users.User;
import com.study.sut.users.UserRejectedException;
import com.study.sut.users.UserService;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import java.util.List;

/**
 * REST API under {@code /api/users}.
 *
 * <pre>
 * POST   /api/users              201 + Location, body: user | 400 invalid | 409 email_taken
 * GET    /api/users[?email=..]   200, list ordered by id (filtered by email when given)
 * GET    /api/users/{id}         200 | 404 user_not_found
 * DELETE /api/users/{id}         204 | 404 user_not_found
 * </pre>
 */
public final class UsersApi {

    private final UserService users;
    private final ObjectMapper json;

    public UsersApi(UserService users, ObjectMapper json) {
        this.users = users;
        this.json = json;
    }

    public void register(Javalin app) {
        app.post("/api/users", this::create);
        app.get("/api/users", this::list);
        app.get("/api/users/{id}", ctx -> ctx.json(users.get(id(ctx))));
        app.delete("/api/users/{id}", ctx -> {
            users.delete(id(ctx));
            ctx.status(HttpStatus.NO_CONTENT);
        });
    }

    private void create(Context ctx) {
        NewUser request;
        try {
            request = json.readValue(ctx.body(), NewUser.class);
        } catch (JsonProcessingException e) {
            throw UserRejectedException.invalid("malformed_json", "Body is not a valid user JSON");
        }
        if (request == null) {
            throw UserRejectedException.invalid("malformed_json", "Body is empty");
        }
        User user = users.create(request);
        ctx.status(HttpStatus.CREATED).header("Location", "/api/users/" + user.id()).json(user);
    }

    private void list(Context ctx) {
        String email = ctx.queryParam("email");
        ctx.json(email == null ? users.all() : users.findByEmail(email).map(List::of).orElse(List.of()));
    }

    static long id(Context ctx) {
        String raw = ctx.pathParam("id");
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw UserRejectedException.invalid("id_invalid", "Id must be a number: " + raw);
        }
    }
}
