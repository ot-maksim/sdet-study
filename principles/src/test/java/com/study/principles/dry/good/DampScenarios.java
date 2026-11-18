package com.study.principles.dry.good;

import com.study.principles.dry.Deployed;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.study.principles.dry.good.ResponseAssert.assertThat;
import static com.study.principles.dry.good.Users.aUser;

/**
 * DAMP: each test tells its story in its own body — the one field that matters and a literal expectation.
 * The mechanics (endpoint, JSON, how to compare) are DRY in {@link UserApi} and {@link ResponseAssert}.
 * Input for {@code DryTest}.
 */
public class DampScenarios {

    private final UserApi users = new UserApi(Deployed.http());
    private final CompanyApi companies = new CompanyApi(Deployed.http());

    @Test
    void anAdultOf18IsAccepted() {
        assertThat(users.create(aUser().aged(18).build())).isCreated().has("age", 18);
    }

    @Test
    void aMinorOf17IsRejected() {
        assertThat(users.create(aUser().aged(17).build())).isRejectedWith("age_too_low");
    }

    @Test
    void nameIsRequired() {
        assertThat(users.create(aUser().named("").build())).isRejectedWith("name_required");
    }

    @Test
    void countryIsStored() {
        assertThat(users.create(aUser().from("DE").build())).isCreated().has("country", "DE");
    }

    @Test
    void companyNameIsRequired() {
        assertThat(companies.create(Map.of("vatId", "DE123"))).hasValidationError("name: required");
    }
}
