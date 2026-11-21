package com.study.project.tests;

import static com.study.project.api.ApiResponseAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.study.project.api.users.NewUser;
import com.study.project.api.users.User;
import com.study.project.assembly.ApiTests;
import com.study.project.domain.UserChecks;
import com.study.project.domain.UserSteps;
import org.junit.jupiter.api.Test;

@ApiTests
class UsersApiTest {

    @Test
    void registeredUserIsStoredExactlyAsReturned(UserSteps users, UserChecks checks) {
        NewUser shouting = users.aNewUser();
        shouting = shouting.withEmail(shouting.email().toUpperCase()).withCountry("gb");

        User ada = users.registerThroughApi(shouting);

        assertThat(ada.email()).isEqualTo(shouting.email().toLowerCase());
        assertThat(ada.country()).isEqualTo("GB");
        checks.assertStoredAsReturned(ada);
    }

    @Test
    void underageIsRejectedAndNothingIsStored(UserSteps users, UserChecks checks) {
        NewUser seventeen = users.aNewUser().withAge(17);

        assertThat(users.tryRegisterThroughApi(seventeen)).isRejectedWith(400, "age_too_low");
        checks.assertNotStored(seventeen.email());
    }

    @Test
    void emailOfAnExistingUserIsTakenIgnoringCase(UserSteps users) {
        User existing = users.existing();

        assertThat(users.tryRegisterThroughApi(users.aNewUser().withEmail(existing.email().toUpperCase())))
                .isRejectedWith(409, "email_taken");
    }
}
