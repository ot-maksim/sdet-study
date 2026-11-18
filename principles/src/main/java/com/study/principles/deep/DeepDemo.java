package com.study.principles.deep;

import com.study.principles.deep.bad.HttpHelper;
import com.study.principles.deep.bad.UserSteps;
import com.study.principles.deep.good.ApiException;
import com.study.principles.deep.good.AuthApi;
import com.study.principles.deep.good.AuthenticatedSession;
import com.study.principles.deep.good.NewUser;
import com.study.principles.deep.good.UserApi;

/**
 * Runs the same scenarios through the shallow and the deep design and prints what a test author would see.
 * <pre>
 * mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.deep.DeepDemo
 * </pre>
 */
public class DeepDemo {

    public static void main(String[] args) {
        System.out.println("=== bad: HttpHelper + UserSteps ===");
        bad();
        System.out.println();
        System.out.println("=== good: AuthApi -> AuthenticatedSession -> UserApi ===");
        good();
    }

    private static void bad() {
        FakeUsersBackend backend = new FakeUsersBackend();
        HttpHelper http = new HttpHelper(backend);

        attempt("forgot login()", () -> new UserSteps(http).createUser("Ann", "ann@test.io", "USER"));

        UserSteps steps = new UserSteps(http);
        steps.loginAsAdmin();
        attempt("invalid email", () -> steps.createUser("Bob", "not-an-email", "USER"));
        System.out.println("  ...the real cause is only reachable via a pass-through: "
                + steps.getStatus() + " " + steps.getResponseBody());
        attempt("name with quotes", () -> steps.createUser("Jane \"JJ\" Doe", "jane@test.io", "USER"));

        int id = new UserSteps(http).createUser("Eve", "eve@test.io", "ADMIN");
        System.out.println("'anonymous' test on the shared helper created ADMIN #" + id
                + " — the admin token from the previous test leaked");
    }

    private static void good() {
        FakeUsersBackend backend = new FakeUsersBackend();
        AuthenticatedSession admin = new AuthApi(backend).login(FakeUsersBackend.ADMIN, FakeUsersBackend.ADMIN_PASSWORD);
        UserApi users = new UserApi(admin);

        System.out.println("forgot login()        -> does not compile: new UserApi(<no session>)");
        attempt("invalid email", () -> users.create(NewUser.user("Bob", "not-an-email")));
        System.out.println("negative test         -> tryCreate: " + users.tryCreate(NewUser.user("Bob", "not-an-email")));
        System.out.println("name with quotes      -> " + users.create(NewUser.user("Jane \"JJ\" Doe", "jane@test.io")));
        System.out.println("session in a log      -> " + admin);
    }

    private static void attempt(String scenario, Runnable action) {
        try {
            action.run();
            System.out.printf("%-21s -> ok%n", scenario);
        } catch (ApiException e) {
            System.out.printf("%-21s -> %s%n", scenario, e.getMessage());
        } catch (RuntimeException e) {
            System.out.printf("%-21s -> %s: %s%n", scenario, e.getClass().getSimpleName(), e.getMessage());
        }
    }
}
