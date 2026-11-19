package com.study.framework.domain;

import static com.study.framework.api.ApiResponseAssert.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

import com.study.framework.api.users.NewUser;
import com.study.framework.api.users.NewUsers;
import com.study.framework.api.users.User;
import com.study.framework.api.users.UserApi;
import com.study.framework.core.lifecycle.Scope;
import com.study.framework.db.users.UserRows;
import com.study.framework.ui.BrowserPool;
import com.study.framework.ui.BrowserProfiles;
import com.study.framework.ui.UiSettings;
import com.study.framework.ui.UiSession;
import com.study.framework.ui.users.UsersPage;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Steps clean up whatever channel created the user — also users created through the UI or by mistake. */
@ExtendWith(StandRoot.class)
class UserStepsTest {

    @Test
    void everyChannelIsCleanedUp(UserApi api, UserRows rows, NewUsers newUsers, UserChecks checks,
                                 BrowserPool browsers, UiSettings ui) {
        Scope testScope = new Scope("simulated test");
        UserSteps users = new UserSteps(new DbUserPrecondition(rows, Clock.systemUTC(), testScope), newUsers, api, testScope);
        NewUser throughUi = users.aNewUser();
        NewUser underage = users.aNewUser().withAge(17);

        User existing = users.existing(u -> u.withCountry("GB"));
        User throughApi = users.registerThroughApi(users.aNewUser());
        try (UiSession session = new UiSession(browsers, BrowserProfiles.get("desktop"), ui)) {
            UsersPage page = new UsersPage(session.page()).open();
            users.registerThroughUi(page, throughUi);
            checks.assertListed(page, existing);
        }
        assertThat(users.tryRegisterThroughApi(underage)).isRejectedWith(400, "age_too_low");
        checks.assertStoredAsReturned(throughApi);
        assertThat(existing.country()).isEqualTo("GB");

        testScope.close();

        assertThat(api.find(existing.id())).isEmpty();
        assertThat(api.find(throughApi.id())).isEmpty();
        assertThat(api.findByEmail(throughUi.email())).isEmpty();
        checks.assertNotStored(underage.email());
    }
}
