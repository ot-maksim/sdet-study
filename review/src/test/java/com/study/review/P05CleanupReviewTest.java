package com.study.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.study.review.bad.api.UserApi;
import com.study.review.bad.data.TestData;
import com.study.review.good.GoodRoot;
import com.study.review.good.api.User;
import com.study.review.good.api.UsersClient;
import com.study.review.good.data.Cleanup;
import com.study.review.good.data.TestUsers;
import com.study.review.good.steps.UserSteps;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** #5 — cleanup by pattern: deletes what other tests still use, keeps what does not match. */
class P05CleanupReviewTest {

    private static final UsersClient stand = new UsersClient(URI.create(Stand.shared().url()), Duration.ofSeconds(5));

    @Nested
    class Bad {

        @Test
        void myCleanupDeletesTheUserOfATestRunningInParallel() {
            Stand.forBadFramework();
            long othersUser = createdId(new UserApi().createUser(TestData.randomUser()).jsonPath().get("id"));

            TestData.cleanUp();   // this test's @AfterEach

            assertThat(stand.find(othersUser)).as("the other test's user, mid-test").isEmpty();
        }

        @Test
        void aUserWithoutThePrefixIsNeverCleanedUp() {
            Stand.forBadFramework();
            Map<String, Object> user = TestData.randomUser();
            user.put("email", "ada.lovelace." + System.nanoTime() + "@example.com");   // a test that needed a "real" email
            long id = createdId(new UserApi().createUser(user).jsonPath().get("id"));

            TestData.cleanUp();

            assertThat(stand.find(id)).as("left on the stand forever").isPresent();
            stand.deleteIfExists(id);
        }
    }

    @Nested
    class Good {

        @Test
        void eachTestRemovesExactlyWhatItCreated() {
            TestUsers data = TestUsers.forNewRun();
            Cleanup mine = new Cleanup();
            Cleanup others = new Cleanup();
            User myUser = new UserSteps(stand, data, mine).existing();
            User othersUser = new UserSteps(stand, data, others).existing();

            mine.close();

            assertThat(stand.find(myUser.id())).isEmpty();
            assertThat(stand.find(othersUser.id())).isPresent();
            others.close();
        }

        @Test
        void cleanupRunsWhenTheTestFails() {
            FailsAfterCreating.created.set(0);

            EngineRuns.run(FailsAfterCreating.class).testEvents().assertStatistics(stats -> stats.failed(1));

            assertThat(FailsAfterCreating.created.get()).isPositive();
            assertThat(stand.find(FailsAfterCreating.created.get())).as("removed after the failure").isEmpty();
        }
    }

    private static long createdId(Object id) {
        return ((Number) id).longValue();
    }

    @ExtendWith(GoodRoot.class)
    static class FailsAfterCreating {
        static final AtomicLong created = new AtomicLong();

        @Test
        void fails(UserSteps users) {
            created.set(users.existing().id());
            throw new AssertionError("the test failed after creating its data");
        }
    }
}
