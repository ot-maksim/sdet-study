package com.study.principles.deep;

import com.study.principles.deep.good.ApiException;
import com.study.principles.deep.good.AuthApi;
import com.study.principles.deep.good.AuthenticatedSession;
import com.study.principles.deep.good.NewUser;
import com.study.principles.deep.good.Role;
import com.study.principles.deep.good.User;
import com.study.principles.deep.good.UserApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.study.principles.deep.FakeUsersBackend.ADMIN;
import static com.study.principles.deep.FakeUsersBackend.ADMIN_PASSWORD;
import static com.study.principles.deep.FakeUsersBackend.ALICE;
import static com.study.principles.deep.FakeUsersBackend.ALICE_PASSWORD;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The same scenarios as {@link BadUserStepsTest}; here each one reads like the requirement. */
@DisplayName("good: deep UserApi, login order enforced by types")
class GoodUserApiTest {

    private final FakeUsersBackend backend = new FakeUsersBackend();
    private final AuthApi auth = new AuthApi(backend);
    private final UserApi asAdmin = new UserApi(auth.login(ADMIN, ADMIN_PASSWORD));

    @Test
    void createReturnsTheUserInDomainTerms() {
        User user = asAdmin.create(NewUser.user("Ann", "ann@test.io"));

        assertThat(user).isEqualTo(new User(user.id(), "Ann", "ann@test.io", Role.USER));
        assertThat(asAdmin.get(user.id())).isEqualTo(user);
    }

    @Test
    void failureSaysWhichCallWhatWasExpectedAndWhatCameBack() {
        assertThatThrownBy(() -> asAdmin.create(NewUser.user("Bob", "not-an-email")))
                .isInstanceOf(ApiException.class)
                .hasMessage("POST /api/users as admin: expected 201 but was 400, body: {\"error\":\"invalid email\"}");
    }

    @Test
    void negativeTestUsesTheLowerLevelOnPurpose() {
        HttpResponse response = asAdmin.tryCreate(NewUser.user("Bob", "not-an-email"));

        assertThat(response.status()).isEqualTo(400);
        assertThat(Json.parse(response.body())).containsEntry("error", "invalid email");
    }

    @Test
    void quotesInDataAreEscapedInOnePlace() {
        User user = asAdmin.create(NewUser.user("Jane \"JJ\" Doe", "jane@test.io"));

        assertThat(user.name()).isEqualTo("Jane \"JJ\" Doe");
    }

    @Test
    void badCredentialsFailAtLoginNotAtTheFirstApiCall() {
        assertThatThrownBy(() -> auth.login(ALICE, "wrong"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("POST /login as alice: expected 200 but was 401");
    }

    @Test
    void anUnauthenticatedUserApiCannotBeBuilt() {
        // new UserApi() and new AuthenticatedSession(..) do not compile; null is the only way left
        assertThatThrownBy(() -> new UserApi(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("session");
    }

    @Test
    void sessionsAreIndependentValuesNothingLeaksBetweenThem() {
        UserApi asAlice = new UserApi(auth.login(ALICE, ALICE_PASSWORD));

        asAdmin.create(NewUser.user("Ann", "ann@test.io"));
        HttpResponse response = asAlice.tryCreate(NewUser.user("Eve", "eve@test.io").withRole(Role.ADMIN));
        asAdmin.create(NewUser.user("Bob", "bob@test.io").withRole(Role.ADMIN));

        assertThat(response.status()).isEqualTo(403);
    }

    @Test
    void everyRequestIsBuiltFromScratch() {
        User ann = asAdmin.create(NewUser.user("Ann", "ann@test.io"));
        asAdmin.get(ann.id());

        HttpRequest get = backend.requests().get(backend.requests().size() - 1);
        assertThat(get.body()).isNull();
        assertThat(get.headers()).containsOnlyKeys("Authorization");
    }

    @Test
    void sessionToStringNeverShowsTheToken() {
        AuthenticatedSession session = auth.login(ALICE, ALICE_PASSWORD);

        assertThat(session).hasToString("AuthenticatedSession[user=alice]");
    }
}
