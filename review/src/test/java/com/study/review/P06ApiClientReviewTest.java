package com.study.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.study.review.bad.data.TestData;
import com.study.review.bad.steps.ScenarioContext;
import com.study.review.bad.steps.UserSteps;
import com.study.review.good.GoodRoot;
import com.study.review.good.api.ApiException;
import com.study.review.good.api.ApiResponse;
import com.study.review.good.api.UsersClient;
import com.study.review.good.data.TestUsers;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * #6 — a client that returns raw responses and steps that never look at the status: a failed precondition
 * passes silently and the test dies later, elsewhere, without the cause.
 */
class P06ApiClientReviewTest {

    @Nested
    class Bad {

        @Test
        void aRejectedCreateFailsTwoStepsLaterAsAnNpe() {
            Stand.forBadFramework();
            ScenarioContext.clear();
            UserSteps steps = new UserSteps();
            Map<String, Object> user = TestData.randomUser();
            user.put("age", 15);   // say, the defaults changed and the "valid" user is not valid any more

            steps.createUser(user);   // no failure here: 400 is just a Response

            Throwable failure = catchThrowable(steps::userShouldExist);
            assertThat(failure).isInstanceOf(NullPointerException.class);
            assertThat(String.valueOf(failure.getMessage())).doesNotContain("400").doesNotContain("age_too_low");
        }
    }

    @Nested
    @ExtendWith(GoodRoot.class)
    class Good {

        @Test
        void aRejectedPreconditionFailsAtTheCallWithTheExchange(UsersClient api, TestUsers data) {
            assertThatThrownBy(() -> api.create(data.aUser().withAge(15)))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("POST")
                    .hasMessageContaining("/api/users")
                    .hasMessageContaining("expected 201, got 400")
                    .hasMessageContaining("age_too_low");
        }

        @Test
        void aNegativeTestAsksForTheResponseExplicitly(UsersClient api, TestUsers data) {
            ApiResponse response = api.tryCreate(data.aUser().withAge(15));

            assertThat(response.status()).isEqualTo(400);
            assertThat(response.errorCode()).isEqualTo("age_too_low");
        }
    }
}
