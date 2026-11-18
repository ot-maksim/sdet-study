package com.study.principles.coupling;

import com.study.principles.coupling.bad.captive.LoginSteps;
import com.study.principles.coupling.bad.locator.DriverManager;
import com.study.principles.coupling.good.Browsers;
import com.study.principles.coupling.good.Pages;

/**
 * The same login scenario through each design.
 * <pre>
 * mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.coupling.CouplingDemo
 * </pre>
 */
public class CouplingDemo {

    public static void main(String[] args) {
        System.out.println("=== bad/newinside: each page does new Browser() ===");
        var login = new com.study.principles.coupling.bad.newinside.LoginPage();
        var dashboard = new com.study.principles.coupling.bad.newinside.DashboardPage();
        login.open();
        login.loginAs("alice", "alice-pass");
        dashboard.open();
        System.out.println("login page in " + login.getBrowser() + ", dashboard in " + dashboard.getBrowser());
        attempt("dashboard greeting", dashboard::greeting);

        System.out.println();
        System.out.println("=== bad/locator: static DriverManager ===");
        var hiddenLogin = new com.study.principles.coupling.bad.locator.LoginPage();
        System.out.println("new LoginPage() constructed fine — it claims to need nothing");
        attempt("open() before DriverManager.set", () -> {
            hiddenLogin.open();
            return "ok";
        });
        Browser previousTests = new FakeBrowser();
        DriverManager.set(previousTests);
        previousTests.close();   // the previous test's teardown closed it, nobody cleared the holder
        attempt("open() in the next test", () -> {
            hiddenLogin.open();
            return "ok";
        });

        System.out.println();
        System.out.println("=== bad/captive: singleton steps over a per-test browser ===");
        try (Browser test1 = new FakeBrowser()) {
            attempt("test 1 with " + test1, () -> LoginSteps.instance(test1).loginAs("alice", "alice-pass"));
        }
        try (Browser test2 = new FakeBrowser()) {
            attempt("test 2 with " + test2, () -> LoginSteps.instance(test2).loginAs("bob", "bob-pass"));
        }

        System.out.println();
        System.out.println("=== good: injected browser, owned by the composition root ===");
        try (Browsers browsers = new Browsers(FakeBrowser::new)) {
            Pages alice = browsers.newSession();
            Pages bob = browsers.newSession();
            attempt("alice", () -> alice.login().open().loginAs("alice", "alice-pass").greeting());
            attempt("bob, side by side", () -> bob.login().open().loginAs("bob", "bob-pass").greeting());
            attempt("alice's dashboard again", () -> alice.dashboard().open().greeting());
            System.out.println("launched: " + browsers.launched());
        }
    }

    private static void attempt(String scenario, java.util.function.Supplier<String> action) {
        try {
            System.out.printf("%-34s -> %s%n", scenario, action.get());
        } catch (RuntimeException e) {
            System.out.printf("%-34s -> %s: %s%n", scenario, e.getClass().getSimpleName(), e.getMessage());
        }
    }
}
