package com.study.principles.parallel;

import io.restassured.builder.ResponseBuilder;
import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;

import java.net.URI;
import java.util.List;

/**
 * The "network": a RestAssured {@link Filter} that answers instead of sending the request, so real
 * RestAssured code runs in-process with no server. It sees exactly the headers RestAssured would send.
 *
 * <p>It is <b>stateless</b> (the shop it holds is thread-safe), so one instance may be shared by every
 * test. A filter that collects something into a plain list (request log for a report, "last response")
 * would be shared mutable state just like a shared spec.
 *
 * <pre>
 * POST   /login           form: username, password  -> 200 {token}
 * POST   /users           form: username (admin)    -> 201 {username, password}
 * DELETE /users/{name}    (admin)                   -> 204
 * GET    /me                                        -> 200 {username, plan, greeting}
 * PUT    /features/{flag}  form: enabled[, username] (admin) -> 204   (no username: global)
 * </pre>
 */
public final class FakeShopHttp implements Filter {

    private final FakeShop shop;

    public FakeShopHttp(FakeShop shop) {
        this.shop = shop;
    }

    @Override
    public Response filter(FilterableRequestSpecification request, FilterableResponseSpecification response,
                           FilterContext context) {
        String path = URI.create(request.getURI()).getPath();
        String route = request.getMethod() + " " + path;
        try {
            if (route.equals("POST /login")) {
                String token = shop.login(form(request, "username"), form(request, "password"));
                return json(200, "{\"token\":\"" + token + "\"}");
            }
            if (route.equals("POST /users")) {
                Credentials created = shop.createUser(bearer(request), form(request, "username"));
                return json(201, "{\"username\":\"" + created.username() + "\",\"password\":\"" + created.password() + "\"}");
            }
            if (route.startsWith("DELETE /users/")) {
                shop.deleteUser(bearer(request), path.substring("/users/".length()));
                return json(204, "{}");
            }
            if (route.equals("GET /me")) {
                FakeShop.Account me = shop.whoIs(bearer(request));
                return json(200, "{\"username\":\"" + me.username() + "\",\"plan\":\"" + me.plan()
                        + "\",\"greeting\":\"" + shop.greeting(me, first(request, "Accept-Language")) + "\"}");
            }
            if (route.startsWith("PUT /features/")) {
                shop.setFeature(bearer(request), path.substring("/features/".length()), form(request, "username"),
                        Boolean.parseBoolean(form(request, "enabled")));
                return json(204, "{}");
            }
            return json(404, "{\"error\":\"no route " + route + "\"}");
        } catch (FakeShop.ShopError e) {
            return json(e.status(), "{\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    private static String form(FilterableRequestSpecification request, String name) {
        return request.getFormParams().get(name);
    }

    private static String bearer(FilterableRequestSpecification request) {
        String value = first(request, "Authorization");
        return value == null ? null : value.replaceFirst("^Bearer ", "");
    }

    /** Like most servers: with several values of a header, the first one wins. */
    private static String first(FilterableRequestSpecification request, String header) {
        List<String> values = request.getHeaders().getValues(header);
        return values.isEmpty() ? null : values.get(0);
    }

    private static Response json(int status, String body) {
        return new ResponseBuilder().setStatusCode(status).setContentType("application/json").setBody(body).build();
    }
}
