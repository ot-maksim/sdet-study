package com.study.review.good.suite;

import static org.assertj.core.api.Assertions.assertThat;

import com.study.review.good.GoodRoot;
import com.study.review.good.api.NewUser;
import com.study.review.good.api.User;
import com.study.review.good.steps.UserSteps;
import com.study.review.good.ui.UsersPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** The team's UI tests after the refactoring: same stories as {@code bad/suite/UsersUiTest}, plus the negative one. */
@ExtendWith(GoodRoot.class)
class UsersPageTest {

    @Test
    void createdUserIsListed(UserSteps users, UsersPage page) {
        NewUser ada = users.aNewUser();
        users.removeAfterTest(ada.email());

        page.open().create(ada);

        assertThat(page.shownEmails()).contains(ada.email());
    }

    @Test
    void userCreatedThroughApiIsListed(UserSteps users, UsersPage page) {
        User ada = users.existing();

        page.open();

        assertThat(page.shownEmails()).contains(ada.email());
    }

    @Test
    void underageUserIsRejectedAndNotListed(UserSteps users, UsersPage page) {
        NewUser young = users.aNewUser().withAge(17);
        users.removeAfterTest(young.email());   // in case the SUT wrongly accepts it

        String error = page.open().rejectedWith(young);

        assertThat(error).isEqualTo("age_too_low");
        assertThat(page.shownEmails()).doesNotContain(young.email());
    }
}
