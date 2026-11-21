package com.study.project.tests;

import static org.assertj.core.api.Assertions.assertThat;

import com.study.project.api.users.NewUser;
import com.study.project.api.users.User;
import com.study.project.api.users.UserApi;
import com.study.project.assembly.UiTests;
import com.study.project.domain.UserChecks;
import com.study.project.domain.UserSteps;
import com.study.project.ui.users.ShownUser;
import com.study.project.ui.users.UserForm;
import com.study.project.ui.users.UsersScreen;
import org.junit.jupiter.api.Test;

/** Runs on whatever project.ui.profile says: -Dspring.profiles.active=mobile runs the same tests on a phone-sized context. */
@UiTests
class UsersPageTest {

    @Test
    void existingUserIsListedAsTheApiReturnsIt(UserSteps users, UserChecks checks, UsersScreen screen) {
        User existing = users.existing(u -> u.withCountry("FR"));

        checks.assertListed(screen, existing);
    }

    @Test
    void formRejectsAnUnderageUserAndKeepsNothing(UserSteps users, UserChecks checks, UsersScreen screen) {
        NewUser kid = users.aNewUser();

        String code = screen.open().createRejected(new UserForm(kid.name(), kid.email(), "17", kid.country())).code();

        assertThat(code).isEqualTo("age_too_low");
        checks.assertNotStored(kid.email());
    }

    @Test
    void deletingOnThePageDeletesTheUser(UserSteps users, UserApi api, UsersScreen screen) {
        User existing = users.existing();

        screen.open().delete(existing.email());

        assertThat(api.find(existing.id())).isEmpty();
        assertThat(screen.notice()).contains("User deleted");
    }

    @Test
    void registeredThroughTheFormIsFoundThroughTheApi(UserSteps users, UserApi api, UsersScreen screen) {
        NewUser bob = users.aNewUser();

        ShownUser shown = users.registerThroughUi(screen.open(), bob);

        assertThat(api.get(shown.id()).email()).isEqualTo(bob.email());
    }
}
