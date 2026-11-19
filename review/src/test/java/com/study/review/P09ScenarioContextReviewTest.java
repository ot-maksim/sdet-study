package com.study.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.study.review.bad.data.TestData;
import com.study.review.bad.steps.ScenarioContext;
import com.study.review.good.GoodRoot;
import com.study.review.good.api.User;
import com.study.review.good.steps.UserSteps;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * #9 — steps talk through a static map: the order of steps is a convention, the types are casts, and two
 * scenarios at once read each other's values. Shown on one thread: the interleaving parallel runs produce.
 */
class P09ScenarioContextReviewTest {

    @Nested
    class Bad {

        @BeforeEach
        void freshContext() {
            Stand.forBadFramework();
            ScenarioContext.clear();
        }

        @Test
        void twoScenariosAtOnceCheckEachOthersUser() {
            com.study.review.bad.steps.UserSteps scenarioA = new com.study.review.bad.steps.UserSteps();
            com.study.review.bad.steps.UserSteps scenarioB = new com.study.review.bad.steps.UserSteps();
            Map<String, Object> ada = TestData.randomUser();
            ada.put("name", "Ada");
            Map<String, Object> bob = TestData.randomUser();
            bob.put("name", "Bob");

            scenarioA.createUser(ada);
            scenarioB.createUser(bob);

            assertThatThrownBy(() -> scenarioA.createdUserShouldHaveName("Ada"))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("Bob");
        }

        @Test
        void aStepCalledOutOfOrderFailsWithoutSayingWhichStepIsMissing() {
            assertThatThrownBy(() -> new com.study.review.bad.steps.UserSteps().userShouldExist())
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @ExtendWith(GoodRoot.class)
    class Good {

        @Test
        void valuesAreLocalVariablesAndTheCompilerChecksTheOrder(UserSteps users) {
            User ada = users.create(users.aNewUser().withName("Ada"));
            User bob = users.create(users.aNewUser().withName("Bob"));

            assertThat(ada.name()).isEqualTo("Ada");
            assertThat(bob.name()).isEqualTo("Bob");
        }
    }
}
