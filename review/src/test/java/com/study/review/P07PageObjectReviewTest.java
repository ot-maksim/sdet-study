package com.study.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.study.review.bad.core.Driver;
import com.study.review.good.GoodRoot;
import com.study.review.good.api.NewUser;
import com.study.review.good.data.TestUsers;
import com.study.review.good.ui.UsersPage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * #7 — a page object that asserts, sleeps and prepares data: a negative scenario cannot be written, every
 * action costs fixed seconds, and the "check" reads the whole HTML, so it passes on the wrong thing.
 */
class P07PageObjectReviewTest {

    @Nested
    class Bad {

        private com.study.review.bad.ui.UsersPage page;

        @BeforeEach
        void openBrowser() {
            Stand.forBadFramework();
            Driver.start();
            page = new com.study.review.bad.ui.UsersPage();
        }

        @AfterEach
        void closeBrowser() {
            Driver.stop();
        }

        @Test
        void aRejectedUserCanOnlyBeSeenAsAFailureInsideThePageObject() {
            long start = System.nanoTime();
            page.open();
            Throwable failure = catchThrowable(() -> page.createUser("Young", "young-" + System.nanoTime() + "@example.com", 15, "GB"));
            long millis = (System.nanoTime() - start) / 1_000_000;

            assertThat(failure).isInstanceOf(AssertionError.class).hasMessageContaining("user is created");
            assertThat(failure.getMessage()).as("which error the form showed").doesNotContain("age_too_low");
            assertThat(millis).as("fixed sleeps, whatever the page does").isGreaterThanOrEqualTo(3_000);
        }

        @Test
        void theListCheckPassesForAUserThatWasNotCreated() {
            String email = "young-" + System.nanoTime() + "@example.com";
            page.open();
            catchThrowable(() -> page.createUser("Young", email, 15, "GB"));

            // the rejected form is re-rendered with the typed values; page.content() contains the email
            page.verifyUserInTable(email);
        }
    }

    @Nested
    @ExtendWith(GoodRoot.class)
    class Good {

        @Test
        void aRejectedUserIsAResultTheTestChecks(UsersPage page, TestUsers data) {
            NewUser young = data.aUser().withAge(15);

            String error = page.open().rejectedWith(young);

            assertThat(error).isEqualTo("age_too_low");
            assertThat(page.shownEmails()).doesNotContain(young.email());
        }
    }
}
