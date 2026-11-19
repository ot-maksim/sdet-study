package com.study.review.good.suite;

import static org.assertj.core.api.Assertions.assertThat;

import com.study.review.good.GoodRoot;
import com.study.review.good.api.ApiResponse;
import com.study.review.good.api.User;
import com.study.review.good.api.UsersClient;
import com.study.review.good.steps.UserSteps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** The team's API tests after the refactoring: same stories as {@code bad/suite/UsersApiTest}. */
@ExtendWith(GoodRoot.class)
class UsersApiTest {

    @Test
    void createsUserWithNormalizedFields(UserSteps users) {
        String email = users.aNewUser().email();

        User created = users.create(users.aNewUser()
                .withName("  Ada Lovelace ")
                .withEmail(email.toUpperCase())
                .withCountry("gb"));

        // Literal expectations: what the API promises, written by the test, not computed by the SUT's code
        assertThat(created.name()).isEqualTo("Ada Lovelace");
        assertThat(created.email()).isEqualTo(email);
        assertThat(created.country()).isEqualTo("GB");
    }

    @Test
    void rejectsUnderageUser(UserSteps users, UsersClient api) {
        ApiResponse response = api.tryCreate(users.aNewUser().withAge(17));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.errorCode()).isEqualTo("age_too_low");
    }

    @Test
    void createdUserCanBeRead(UserSteps users, UsersClient api) {
        User ada = users.existing();

        assertThat(api.find(ada.id())).contains(ada);
    }
}
