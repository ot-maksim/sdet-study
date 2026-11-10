package com.study.principles.parallel.good;

import com.study.principles.parallel.Credentials;
import io.restassured.filter.Filter;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import java.util.Objects;

import static io.restassured.RestAssured.given;

/**
 * Immutable API client: safe to share between parallel tests <i>by construction</i>, not by discipline.
 *
 * <ul>
 *   <li>All fields are final and immutable (a record, a stateless filter, strings).</li>
 *   <li>"Changing" it ({@link #as}, {@link #withLanguage}) returns a <b>new</b> client; the original is
 *       untouched, so a test cannot leak its token or language into anyone else.</li>
 *   <li>The mutable RestAssured {@link RequestSpecification} is created by {@code given()} for
 *       <b>one</b> request and never leaves {@link #request()}: share the recipe, not the product.</li>
 * </ul>
 */
public final class ShopApi {

    public record Me(String username, String plan, String greeting) {
    }

    private final ShopConfig config;
    private final Filter transport;
    private final String token;     // null = anonymous
    private final String language;

    public ShopApi(ShopConfig config, Filter transport) {
        this(config, transport, null, config.defaultLanguage());
    }

    private ShopApi(ShopConfig config, Filter transport, String token, String language) {
        this.config = Objects.requireNonNull(config);
        this.transport = Objects.requireNonNull(transport);
        this.token = token;
        this.language = Objects.requireNonNull(language);
    }

    /** The same client acting as the owner of {@code token}. */
    public ShopApi as(String token) {
        return new ShopApi(config, transport, Objects.requireNonNull(token), language);
    }

    public ShopApi withLanguage(String language) {
        return new ShopApi(config, transport, token, language);
    }

    public String login(Credentials credentials) {
        return request()
                .formParam("username", credentials.username())
                .formParam("password", credentials.password())
                .post("/login")
                .then().statusCode(200)
                .extract().path("token");
    }

    /** Needs an admin client: {@code api.as(adminToken).createUser(..)}. */
    public Credentials createUser(String username) {
        Response response = request().formParam("username", username).post("/users");
        response.then().statusCode(201);
        return new Credentials(response.path("username"), response.path("password"));
    }

    public void deleteUser(String username) {
        request().delete("/users/" + username).then().statusCode(204);
    }

    /** Global: changes the SUT for every test running now. Needs an admin client. */
    public void setFeature(String flag, boolean enabled) {
        request().formParam("enabled", enabled).put("/features/" + flag).then().statusCode(204);
    }

    /** Only for {@code username}: isolation by data, no other test notices. Needs an admin client. */
    public void setFeatureFor(String flag, String username, boolean enabled) {
        request().formParam("enabled", enabled).formParam("username", username)
                .put("/features/" + flag).then().statusCode(204);
    }

    public Me me() {
        Response response = request().get("/me");
        response.then().statusCode(200);
        return new Me(response.path("username"), response.path("plan"), response.path("greeting"));
    }

    public int meStatus() {
        return request().get("/me").statusCode();
    }

    private RequestSpecification request() {
        RequestSpecification request = given()          // a new mutable spec, confined to this one call
                .baseUri(config.baseUri())
                .filter(transport)
                .header("Accept-Language", language);
        return token == null ? request : request.auth().preemptive().oauth2(token);
    }
}
