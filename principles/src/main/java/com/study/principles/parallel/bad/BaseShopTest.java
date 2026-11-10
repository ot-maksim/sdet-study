package com.study.principles.parallel.bad;

import com.study.principles.parallel.Credentials;
import com.study.principles.parallel.FakeShop;
import com.study.principles.parallel.FakeShopHttp;
import com.study.principles.parallel.FakeWebDriver;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;

/**
 * Base class of a suite that grew up running sequentially; then someone set
 * {@code junit.jupiter.execution.parallel.enabled=true}.
 *
 * <h2>What is wrong</h2>
 * Everything a test changes lives in {@code static} fields: one object for every test in the JVM.
 * <ul>
 *   <li><b>{@code api}: one {@link RequestSpecification}.</b> It is a mutable builder, and its methods
 *       return {@code this}. {@code api.auth().preemptive().oauth2(..)} <i>overwrites</i> the shared auth:
 *       last writer wins, so in parallel test A sends test B's token. {@code api.header(..)}
 *       <i>appends</i>: the value stays for every later test, even in a sequential run.
 *       ({@code api.given()} is the same object too.)</li>
 *   <li><b>{@code driver}: one browser session.</b> Cookies and the current page belong to whoever
 *       touched it last; one test's {@code quit()} kills everyone's browser.</li>
 *   <li><b>{@code counter++}</b> is read-modify-write: two threads get the same "unique" name → 409.</li>
 *   <li><b>{@code createdUsers} is a {@link HashMap}:</b> concurrent {@code put} loses entries (users never
 *       cleaned up) or corrupts the map.</li>
 *   <li><b>{@code PREMIUM}</b>: every test uses the same pre-provisioned account; the shop allows one
 *       session per premium account, so a test's login ends the other test's session.</li>
 * </ul>
 * None of this is visible in a sequential run except the header leak, and that one only fails
 * depending on test order.
 */
public abstract class BaseShopTest {

    protected static final Credentials PREMIUM = FakeShop.PREMIUM_ACCOUNTS.get(0);

    protected static FakeShop shop;
    protected static RequestSpecification api;
    protected static FakeWebDriver driver;
    protected static final Map<String, String> createdUsers = new HashMap<>(); // username -> password
    private static int counter;

    @BeforeAll
    public static void startClients() {
        shop = new FakeShop(); // stands for "connect to the stand from config"
        api = new RequestSpecBuilder()
                .setBaseUri("http://shop.test")
                .addFilter(new FakeShopHttp(shop))
                .build();
        driver = new FakeWebDriver(shop);
    }

    @AfterAll
    public static void stopClients() {
        authorizeAs(FakeShop.ADMIN);
        createdUsers.keySet().forEach(name -> given().spec(api).delete("/users/" + name));
        createdUsers.clear();
        driver.quit();
    }

    // ---- API helpers -------------------------------------------------------------------------------

    protected static String login(Credentials credentials) {
        return given().spec(api)
                .formParam("username", credentials.username())
                .formParam("password", credentials.password())
                .post("/login")
                .then().statusCode(200)
                .extract().path("token");
    }

    /** Every later request of <i>every</i> test now goes as this user. */
    protected static void authorizeAs(Credentials credentials) {
        api.auth().preemptive().oauth2(login(credentials));
    }

    /** Appends to the shared spec: there is no "undo", and the first value wins on the server. */
    protected static void useLanguage(String language) {
        api.header("Accept-Language", language);
    }

    protected Credentials createUser() {
        authorizeAs(FakeShop.ADMIN);
        String username = "user-" + (++counter);
        String password = given().spec(api)
                .formParam("username", username)
                .post("/users")
                .then().statusCode(201)
                .extract().path("password");
        createdUsers.put(username, password);
        return new Credentials(username, password);
    }

    protected String whoAmI() {
        return given().spec(api).get("/me").then().statusCode(200).extract().path("username");
    }

    protected String greetingViaApi() {
        return given().spec(api).get("/me").then().statusCode(200).extract().path("greeting");
    }

    protected int meStatus() {
        return given().spec(api).get("/me").statusCode();
    }

    // ---- UI helpers --------------------------------------------------------------------------------

    /** The usual speed-up: log in through the API and put the session cookie into the browser. */
    protected void openProfileAs(Credentials credentials) {
        driver.addCookie("session", login(credentials));
        driver.get("/profile");
    }

    protected String greetingOnPage() {
        return driver.findText("greeting");
    }

    protected String currentUrl() {
        return driver.getCurrentUrl();
    }
}
