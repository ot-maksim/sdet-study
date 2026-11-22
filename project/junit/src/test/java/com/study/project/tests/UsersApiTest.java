package com.study.project.tests;

import static com.study.project.api.ApiResponseAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.study.project.api.users.NewUser;
import com.study.project.api.users.User;
import com.study.project.assembly.ApiTests;
import com.study.project.domain.UserChecks;
import com.study.project.domain.UserSteps;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

    /** A field that is absent from the request (not {@code null} on the wire) is a validation error of its own. */
    @ParameterizedTest(name = "without {0} -> {2}")
    @MethodSource("missingFields")
    void missingFieldIsRejected(String field, UnaryOperator<NewUser> remove, String code, UserSteps users) {
        NewUser incomplete = remove.apply(users.aNewUser());

        assertThat(users.tryRegisterThroughApi(incomplete)).isRejectedWith(400, code);
    }

    static Stream<Arguments> missingFields() {
        return Stream.of(
                Arguments.of("name", (UnaryOperator<NewUser>) NewUser::withoutName, "name_blank"),
                Arguments.of("email", (UnaryOperator<NewUser>) NewUser::withoutEmail, "email_invalid"),
                Arguments.of("age", (UnaryOperator<NewUser>) NewUser::withoutAge, "age_missing"),
                Arguments.of("country", (UnaryOperator<NewUser>) NewUser::withoutCountry, "country_invalid"));
    }
}
