package com.study.principles.dry;

import com.study.principles.dry.bad.TestData;
import com.study.principles.dry.bad.UserScenario;
import com.study.principles.dry.good.ResponseAssert;
import com.study.principles.dry.good.UserApi;

import java.util.Map;

import static com.study.principles.dry.good.Users.aUser;

/**
 * Deploys broken builds and shows what the over-dried and the DAMP check say about each.
 * Run: {@code mvn -q -pl principles compile exec:java -Dmain.class=com.study.principles.dry.DryDemo}
 */
public final class DryDemo {

    public static void main(String[] args) {
        System.out.println("== 1. Build that drops the country ==");
        Deployed.deploy(Release.CURRENT.losingCountry());
        report("bad ", () -> UserScenario.run(TestData.validUser(), 201, null, true));
        report("good", () -> ResponseAssert.assertThat(new UserApi(Deployed.http()).create(aUser().from("DE").build()))
                .isCreated().has("country", "DE"));

        System.out.println();
        System.out.println("== 2. Build with min age 21 (the rule is 18) ==");
        Deployed.deploy(Release.CURRENT.withMinAge(21));
        report("bad ", () -> {
            Map<String, Object> user = TestData.validUser();
            user.put("age", UserScenario.minAge());
            UserScenario.run(user, 201, null, true);
        });
        report("good", () -> ResponseAssert.assertThat(new UserApi(Deployed.http()).create(aUser().aged(18).build()))
                .isCreated());

        System.out.println();
        System.out.println("== 3. Correct build, after one test tweaked the shared template ==");
        Deployed.reset();
        TestData.restoreTemplate();
        TestData.validUser().put("name", "");
        report("bad ", () -> UserScenario.run(TestData.validUser(), 201, null, true));
        report("good", () -> ResponseAssert.assertThat(new UserApi(Deployed.http()).create(aUser().build()))
                .isCreated());
    }

    private static void report(String style, Runnable check) {
        try {
            check.run();
            System.out.println(style + ": GREEN");
        } catch (AssertionError e) {
            System.out.println(style + ": RED   " + e.getMessage().strip().replace("\n", "\n            "));
        }
    }
}
