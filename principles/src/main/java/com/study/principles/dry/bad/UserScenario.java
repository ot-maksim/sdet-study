package com.study.principles.dry.bad;

import com.study.principles.dry.Deployed;
import com.study.principles.dry.HttpResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The universal scenario: every user test is one call. Nothing is repeated — and nothing is said: a reader of
 * {@code run(user, 400, "age_too_low", false)} has to open this class to learn what {@code false} skips.
 */
public final class UserScenario {

    public static final String USERS = "/api/v1/users";

    private UserScenario() {
    }

    public static HttpResponse run(Map<String, Object> user, int expectedStatus, String expectedError,
                                   boolean verifyFields) {
        HttpResponse response = Deployed.http().post(USERS, user);
        assertThat(response.status()).isEqualTo(expectedStatus);
        if (expectedError != null) {
            Checks.assertRejected(response, expectedError, false);
        }
        if (verifyFields) {
            Checks.verifyUser(response, user);
        }
        return response;
    }

    /**
     * "Single source of truth": the boundary comes from the app's own settings, so the tests never go out of date.
     * They also never disagree with the app — whatever the app does is what they expect.
     */
    public static int minAge() {
        return Deployed.release().minAge();
    }
}
