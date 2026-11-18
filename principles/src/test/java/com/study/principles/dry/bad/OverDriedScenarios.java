package com.study.principles.dry.bad;

import com.study.principles.dry.Deployed;
import com.study.principles.dry.HttpResponse;
import com.study.principles.dry.Release;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Map;

import static com.study.principles.dry.bad.TestData.validUser;
import static com.study.principles.dry.bad.UserScenario.minAge;
import static com.study.principles.dry.bad.UserScenario.run;

/**
 * Over-dried user tests: nothing repeated, nothing said. Input for {@code DryTest}; the name does not end in
 * {@code Test}, so Surefire does not run it on its own. The order is fixed to make the leak reproducible —
 * with the default order it moves between machines and JUnit versions.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class OverDriedScenarios {

    @Test
    @Order(1)
    void adultIsAccepted() {
        Map<String, Object> user = validUser();
        user.put("age", minAge());
        run(user, 201, null, true);
    }

    @Test
    @Order(2)
    void minorIsRejected() {
        Map<String, Object> user = validUser();
        user.put("age", minAge() - 1);
        run(user, 400, "age_too_low", false);
    }

    @Test
    @Order(3)
    void nameIsRequired() {
        Map<String, Object> user = validUser();
        user.put("name", "");
        run(user, 400, "name_required", false);
    }

    @Test
    @Order(4)
    void userIsCreated() {
        run(validUser(), 201, null, true);
    }

    @Test
    @Order(5)
    void companyNameIsRequired() {
        HttpResponse response = Deployed.http().post(Release.COMPANIES_PATH, Map.of("vatId", "DE123"));
        Checks.assertRejected(response, "name: required", true);
    }
}
