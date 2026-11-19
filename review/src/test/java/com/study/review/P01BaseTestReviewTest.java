package com.study.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.study.review.bad.suite.UsersApiTest;
import com.study.review.good.GoodRoot;
import com.study.review.good.api.User;
import com.study.review.good.steps.UserSteps;
import java.sql.SQLException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.testkit.engine.EngineExecutionResults;

/** #1 — the god base class: every test pays for everything, and fails for anything. */
class P01BaseTestReviewTest {

    @Nested
    class Bad {

        @Test
        void apiTestsDoNotRunWhenTheDatabaseIsDown() {
            Stand.forBadFramework();   // the SUT is up, its database (for the framework) is not

            EngineExecutionResults results = EngineRuns.run(UsersApiTest.class);

            // Three API tests, none of them started: BaseTest's @BeforeAll launched a browser, then died on JDBC
            assertThat(results.testEvents().started().count()).as("API tests started").isZero();
            Throwable failure = results.containerEvents().failed().stream()
                    .map(e -> e.getRequiredPayload(org.junit.platform.engine.TestExecutionResult.class).getThrowable().orElseThrow())
                    .findFirst().orElseThrow();
            assertThat(failure).isInstanceOf(SQLException.class).hasMessageContaining("localhost:1");
            // and @AfterAll closed a connection that was never opened
            assertThat(failure.getSuppressed()).anySatisfy(s -> assertThat(s).isInstanceOf(NullPointerException.class));
        }
    }

    @Nested
    class Good {

        @Test
        void anApiTestGetsOnlyWhatItDeclares() {
            int launchedBefore = GoodRoot.BROWSER_LAUNCHES.get();

            EngineRuns.run(GoodApiOnly.class).testEvents().assertStatistics(stats -> stats.started(1).succeeded(1));
            assertThat(GoodRoot.BROWSER_LAUNCHES.get()).as("browsers launched for an API test").isEqualTo(launchedBefore);
        }
    }

    /** Input for the engine run: an API test on the refactored framework; no database, no browser asked for. */
    @ExtendWith(GoodRoot.class)
    static class GoodApiOnly {
        @Test
        void createsUser(UserSteps users) {
            User ada = users.existing();

            assertThat(ada.id()).isPositive();
        }
    }
}
