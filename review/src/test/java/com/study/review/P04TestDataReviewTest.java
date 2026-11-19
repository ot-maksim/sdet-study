package com.study.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.study.review.bad.data.TestData;
import com.study.review.good.data.TestUsers;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** #4 — "random" test data from a tiny space: unique columns collide, a run fails with 409 now and then. */
class P04TestDataReviewTest {

    @Nested
    class Bad {

        @Test
        void twoHundredRandomEmailsAlreadyCollide() {
            List<Object> emails = IntStream.range(0, 200).mapToObj(i -> TestData.randomUser().get("email")).toList();

            // 1000 values: a 50% chance of a duplicate at ~38 users, practically certain at 200 (birthday problem)
            assertThat(new HashSet<>(emails)).hasSizeLessThan(200);
        }
    }

    @Nested
    class Good {

        @Test
        void emailsAreUniqueWithinARunAndAcrossRuns() {
            TestUsers run = TestUsers.forNewRun();
            Set<String> emails = new HashSet<>();
            IntStream.range(0, 10_000).forEach(i -> emails.add(run.email("ada")));

            assertThat(emails).hasSize(10_000);
            assertThat(TestUsers.forNewRun().email("ada")).isNotIn(emails);
        }

        @Test
        void anEmailTellsWhichRunCreatedIt() {
            assertThat(new TestUsers("k3x9q").email("ada")).isEqualTo("ada-k3x9q-1@example.test");
        }
    }
}
