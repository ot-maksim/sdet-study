package com.study.principles.encapsulation;

import com.study.principles.encapsulation.good.TestConfig;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Same scenarios against both designs:
 *  1. default config: an API call and a UI page
 *  2. someone "fixes" the config for one client
 *  3. a test switches the host mid-run, then the next test runs
 *  4. (good only) a broken config at startup
 *
 * mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.encapsulation.EncapsulationDemo
 */
public class EncapsulationDemo {

    public static void main(String[] args) {
        bad();
        good();
    }

    private static void bad() {
        System.out.println("=== BAD: global mutable TestConfig, every client knows HOW ===");
        var cfg = com.study.principles.encapsulation.bad.TestConfig.get();
        var transport = new FakeTransport();
        var api = new com.study.principles.encapsulation.bad.UsersApiClient(transport);
        var login = new com.study.principles.encapsulation.bad.LoginPage(transport);

        System.out.println("-- 1. defaults: baseUrl='" + cfg.getBaseUrl() + "', timeout=" + cfg.getTimeout());
        api.getUser(1);
        print(transport);
        login.open();
        print(transport);

        System.out.println("-- 2. API tests were flaky, someone 'fixes' it: no trailing slash, timeout=5000");
        cfg.setBaseUrl("http://localhost:7070");
        cfg.setTimeout(5000);
        api.getUser(1);
        print(transport);
        login.open();
        print(transport);

        System.out.println("-- 3. AdminSmokeTest switches the host, then the users test runs");
        new com.study.principles.encapsulation.bad.AdminSmokeTest(transport).adminCanSeeUser();
        print(transport);
        api.getUser(1);
        print(transport);
        System.out.println("   ^ the users test now talks to " + cfg.getBaseUrl() + " — and so does everything after it");
    }

    private static void good() {
        System.out.println("\n=== GOOD: immutable TestConfig, clients ask WHAT they need ===");
        TestConfig config = TestConfig.from(Map.of("baseUrl", "http://localhost:7070/", "timeout.ms", "5000"));
        var transport = new FakeTransport();
        var api = new com.study.principles.encapsulation.good.UsersApiClient(config, transport);
        var login = new com.study.principles.encapsulation.good.LoginPage(config, transport);

        System.out.println("-- 1. " + config);
        api.getUser(1);
        print(transport);
        login.open();
        print(transport);

        System.out.println("-- 2. trailing slash or not — same result, the config normalizes it once");
        var noSlash = TestConfig.from(Map.of("baseUrl", "http://localhost:7070"));
        new com.study.principles.encapsulation.good.LoginPage(noSlash, transport).open();
        print(transport);

        System.out.println("-- 3. AdminSmokeTest uses withBaseUrl(..) — a copy; then the users test runs");
        new com.study.principles.encapsulation.good.AdminSmokeTest(config, transport).adminCanSeeUser();
        print(transport);
        api.getUser(1);
        print(transport);

        System.out.println("-- 4. broken config: fails at startup with every problem listed");
        try {
            TestConfig.from(Map.of("baseUrl", "localhost:7070", "timeout.ms", "-1"));
        } catch (IllegalArgumentException e) {
            System.out.println("   " + e.getMessage().replace("\n", "\n   "));
        }
    }

    private static void print(FakeTransport transport) {
        FakeTransport.Call call = transport.last();
        List<String> problems = problems(call);
        System.out.println("   " + call + (problems.isEmpty() ? "" : "   <-- " + String.join(", ", problems)));
    }

    /** Demo-only sanity checks, so the output stays honest when you change the inputs. */
    private static List<String> problems(FakeTransport.Call call) {
        List<String> problems = new ArrayList<>();
        String url = call.url();
        int afterScheme = url.indexOf("://") + 3;
        if (url.indexOf("//", afterScheme) >= 0) problems.add("double slash");
        try {
            URI uri = new URI(url);
            if (uri.getHost() == null) problems.add("no valid host (path glued to the port?)");
        } catch (Exception e) {
            problems.add("not a URL");
        }
        if (call.timeout().compareTo(Duration.ofMillis(100)) < 0) problems.add("timeout too short");
        if (call.timeout().compareTo(Duration.ofMinutes(5)) > 0) problems.add("timeout too long");
        return problems;
    }
}
