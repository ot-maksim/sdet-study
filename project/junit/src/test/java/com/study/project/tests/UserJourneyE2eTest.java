package com.study.project.tests;

import static org.assertj.core.api.Assertions.assertThat;

import com.study.project.api.users.NewUser;
import com.study.project.api.users.User;
import com.study.project.api.users.UserApi;
import com.study.project.assembly.E2eTests;
import com.study.project.domain.UserChecks;
import com.study.project.domain.UserSteps;
import com.study.project.ui.users.ShownUser;
import com.study.project.ui.users.UsersScreen;
import org.junit.jupiter.api.Test;

/** One business flow across every channel: what the API returns is what the table holds is what the page shows. */
@E2eTests
class UserJourneyE2eTest {

    @Test
    void apiToDatabaseToPage(UserSteps users, UserChecks checks, UsersScreen screen) {
        User ada = users.registerThroughApi(users.aNewUser().withCountry("it"));

        checks.assertStoredAsReturned(ada);
        checks.assertListed(screen, ada);
    }

    @Test
    void pageToApiToDatabaseAndBack(UserSteps users, UserChecks checks, UserApi api, UsersScreen screen) {
        NewUser bob = users.aNewUser();

        ShownUser shown = users.registerThroughUi(screen.open(), bob);
        User viaApi = api.get(shown.id());
        checks.assertStoredAsReturned(viaApi);

        screen.open().delete(viaApi.email());
        assertThat(api.find(viaApi.id())).isEmpty();
        checks.assertNotStored(viaApi.email());
    }
}
