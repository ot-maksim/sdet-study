package com.study.project.tests;

import static com.study.project.api.ApiResponseAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.study.project.api.users.NewUser;
import com.study.project.api.users.User;
import com.study.project.assembly.Groups;
import com.study.project.domain.UserChecks;
import com.study.project.domain.UserSteps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.testng.annotations.Test;

@Test(groups = Groups.API)
public class UsersApiTest {

    @Test
    public void registeredUserIsStoredExactlyAsReturned(UserSteps users, UserChecks checks) {
        NewUser shouting = users.aNewUser();
        shouting = shouting.withEmail(shouting.email().toUpperCase()).withCountry("gb");

        User ada = users.registerThroughApi(shouting);

        assertThat(ada.email()).isEqualTo(shouting.email().toLowerCase());
        assertThat(ada.country()).isEqualTo("GB");
        checks.assertStoredAsReturned(ada);
    }

    @Test
    public void underageIsRejectedAndNothingIsStored(UserSteps users, UserChecks checks) {
        NewUser seventeen = users.aNewUser().withAge(17);

        assertThat(users.tryRegisterThroughApi(seventeen)).isRejectedWith(400, "age_too_low");
        checks.assertNotStored(seventeen.email());
    }

    @Test
    public void emailOfAnExistingUserIsTakenIgnoringCase(UserSteps users) {
        User existing = users.existing();

        assertThat(users.tryRegisterThroughApi(users.aNewUser().withEmail(existing.email().toUpperCase())))
                .isRejectedWith(409, "email_taken");
    }

    /**
     * A field that is absent from the request (not {@code null} on the wire) is a validation error of its own.
     * One test over a table, not a data provider: the listener's provider injects the fixtures (assembly), and
     * TestNG takes one provider per method. Rows that fail are collected, so one run names all of them.
     */
    @Test
    public void missingFieldIsRejected(UserSteps users) {
        Map<String, UnaryOperator<NewUser>> removals = new LinkedHashMap<>();
        removals.put("name_blank", NewUser::withoutName);
        removals.put("email_invalid", NewUser::withoutEmail);
        removals.put("age_missing", NewUser::withoutAge);
        removals.put("country_invalid", NewUser::withoutCountry);

        List<String> failures = new ArrayList<>();
        removals.forEach((code, remove) -> {
            try {
                assertThat(users.tryRegisterThroughApi(remove.apply(users.aNewUser()))).isRejectedWith(400, code);
            } catch (AssertionError e) {
                failures.add(code + ": " + e.getMessage());
            }
        });

        assertThat(failures).isEmpty();
    }
}
