package com.study.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.study.review.bad.api.UserApi;
import com.study.review.good.api.NewUser;
import com.study.review.good.api.UsersClient;
import io.restassured.RestAssured;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * #2 — static state: {@code RestAssured.baseURI} (and the same pattern in {@code Driver}, {@code BaseTest.db},
 * {@code ScenarioContext}). Two test classes, each pointing at its own stand, run in parallel; latches force the
 * interleaving that a CI run produces by chance.
 */
class P02SharedStateReviewTest {

    static Stand one;
    static Stand two;
    static CountDownLatch oneConfigured;
    static CountDownLatch twoConfigured;

    @BeforeEach
    void freshStandsAndInterleaving() {
        one = Stand.start();
        two = Stand.start();
        oneConfigured = new CountDownLatch(1);
        twoConfigured = new CountDownLatch(1);
    }

    @AfterEach
    void stopStands() {
        one.close();
        two.close();
        RestAssured.reset();
    }

    @Nested
    class Bad {

        @Test
        void aParallelClassRedirectsAnotherClassesRequests() {
            EngineRuns.runInParallel(BadOnStandOne.class, BadOnStandTwo.class)
                    .testEvents().assertStatistics(stats -> stats.started(2).succeeded(2));   // both green...

            // ...and both users went to stand two
            assertThat(users(one)).as("users on stand one").isZero();
            assertThat(users(two)).as("users on stand two").isEqualTo(2);
        }
    }

    @Nested
    class Good {

        @Test
        void eachClientKeepsItsOwnStand() {
            EngineRuns.runInParallel(GoodOnStandOne.class, GoodOnStandTwo.class)
                    .testEvents().assertStatistics(stats -> stats.started(2).succeeded(2));

            assertThat(users(one)).as("users on stand one").isEqualTo(1);
            assertThat(users(two)).as("users on stand two").isEqualTo(1);
        }
    }

    private static int users(Stand stand) {
        return client(stand).all().size();
    }

    private static UsersClient client(Stand stand) {
        return new UsersClient(java.net.URI.create(stand.url()), Duration.ofSeconds(5));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("the other class did not start: is parallel execution on?");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static Map<String, Object> user(String email) {
        return Map.of("name", "Ada", "email", email, "age", 36, "country", "GB");
    }

    // Input for the engine runs. The bad classes configure RestAssured the way BaseTest does.

    static class BadOnStandOne {
        @BeforeAll
        static void configure() {
            RestAssured.baseURI = one.url();
            oneConfigured.countDown();
            await(twoConfigured);
        }

        @Test
        void createsUser() {
            assertThat(new UserApi().createUser(user("one@example.test")).statusCode()).isEqualTo(201);
        }
    }

    static class BadOnStandTwo {
        @BeforeAll
        static void configure() {
            await(oneConfigured);
            RestAssured.baseURI = two.url();
            twoConfigured.countDown();
        }

        @Test
        void createsUser() {
            assertThat(new UserApi().createUser(user("two@example.test")).statusCode()).isEqualTo(201);
        }
    }

    static class GoodOnStandOne {
        static UsersClient api;

        @BeforeAll
        static void configure() {
            api = client(one);
            oneConfigured.countDown();
            await(twoConfigured);
        }

        @Test
        void createsUser() {
            api.create(new NewUser("Ada", "one@example.test", 36, "GB"));
        }
    }

    static class GoodOnStandTwo {
        static UsersClient api;

        @BeforeAll
        static void configure() {
            await(oneConfigured);
            api = client(two);
            twoConfigured.countDown();
        }

        @Test
        void createsUser() {
            api.create(new NewUser("Ada", "two@example.test", 36, "GB"));
        }
    }
}
