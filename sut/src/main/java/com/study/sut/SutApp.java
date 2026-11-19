package com.study.sut;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.study.sut.users.UserRejectedException;
import com.study.sut.users.UserService;
import com.study.sut.web.ApiError;
import com.study.sut.web.UsersApi;
import com.study.sut.web.UsersPage;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.json.JavalinJackson;
import java.util.Map;

/** Builds the web application around a {@link UserService}; knows nothing about where users are stored. */
public final class SutApp {

    private SutApp() {
    }

    public static ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public static Javalin create(UserService users) {
        ObjectMapper json = objectMapper();
        Javalin app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.jsonMapper(new JavalinJackson(json, false));
        });

        app.get("/health", ctx -> ctx.json(Map.of("status", "UP")));
        new UsersApi(users, json).register(app);
        new UsersPage(users).register(app);

        app.exception(UserRejectedException.class, (e, ctx) -> ctx
                .status(switch (e.reason()) {
                    case INVALID -> HttpStatus.BAD_REQUEST;
                    case CONFLICT -> HttpStatus.CONFLICT;
                    case NOT_FOUND -> HttpStatus.NOT_FOUND;
                })
                .json(new ApiError(e.code(), e.getMessage())));
        return app;
    }
}
