package com.study.principles.deep;

import com.study.principles.deep.bad.HttpHelper;
import com.study.principles.deep.bad.UserSteps;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Characterization tests: they PASS because they pin down what the shallow design does.
 * Every failure surfaces as the same {@code NumberFormatException}; the real cause is reachable only
 * through pass-through getters, and only if the test knows to ask.
 */
@DisplayName("bad: shallow HttpHelper + UserSteps with temporal coupling")
class BadUserStepsTest {

    private final FakeUsersBackend backend = new FakeUsersBackend();
    private final HttpHelper http = new HttpHelper(backend);
    private final UserSteps steps = new UserSteps(http);

    @Test
    void forgettingLoginFailsFarFromTheCause() {
        assertThatThrownBy(() -> steps.createUser("Ann", "ann@test.io", "USER"))
                .isInstanceOf(NumberFormatException.class)
                .hasMessageNotContaining("401");

        assertThat(steps.getStatus()).isEqualTo(401);   // the real reason, if you know where to look
    }

    @Test
    void negativeTestHasToCatchTheWrongExceptionAndAskAgain() {
        steps.loginAsAdmin();

        try {
            steps.createUser("Bob", "not-an-email", "USER");
        } catch (NumberFormatException expected) {
            // the only way to "expect" a 400 with this API
        }

        assertThat(steps.getStatus()).isEqualTo(400);
        assertThat(steps.getResponseBody()).contains("invalid email");
    }

    @Test
    void stringFormatJsonBreaksOnQuotesInData() {
        steps.loginAsAdmin();

        assertThatThrownBy(() -> steps.createUser("Jane \"JJ\" Doe", "jane@test.io", "USER"))
                .isInstanceOf(NumberFormatException.class);
        assertThat(steps.getResponseBody()).contains("malformed json");
    }

    @Test
    void responseIsReadableBeforeAnythingWasSent() {
        assertThat(http.getStatus()).isZero();
        assertThat(http.getBody()).isNull();   // no error: just a null that explodes somewhere later
    }

    @Test
    void tokenFromOneTestLeaksIntoTheNextThroughTheSharedHelper() {
        // "test 1": an admin scenario
        steps.loginAsAdmin();
        steps.createUser("Ann", "ann@test.io", "USER");

        // "test 2": meant to run as nobody, reuses the helper (a field in a BaseTest, a static, a DI singleton)
        int id = new UserSteps(http).createUser("Eve", "eve@test.io", "ADMIN");

        assertThat(id).isPositive();   // an anonymous caller just created an admin
        assertThat(backend.requests().get(2).header("Authorization")).startsWith("Bearer t-1-admin");
    }

    @Test
    void getCarriesTheBodyOfThePreviousPost() {
        steps.loginAsAdmin();
        int id = steps.createUser("Ann", "ann@test.io", "USER");

        http.get("/api/users/" + id);

        HttpRequest get = backend.requests().get(2);
        assertThat(get.method()).isEqualTo("GET");
        assertThat(get.body()).contains("ann@test.io");
        assertThat(get.header("Content-Type")).isEqualTo("application/json");
    }
}
