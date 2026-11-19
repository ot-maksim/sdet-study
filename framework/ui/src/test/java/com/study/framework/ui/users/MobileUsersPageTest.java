package com.study.framework.ui.users;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Page;
import com.study.framework.core.data.Unique;
import com.study.framework.core.lifecycle.Cleanup;
import com.study.framework.ui.BrowserPool;
import com.study.framework.ui.BrowserProfiles;
import com.study.framework.ui.UiSession;
import com.study.framework.ui.UiSettings;
import com.study.framework.ui.UiTestRoot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** The same contract on a phone-sized, touch, mobile-UA context: the page object does not change. */
@ExtendWith(UiTestRoot.class)
class MobileUsersPageTest implements UsersScreenContract {

    private Page browserPage;
    private UsersPage page;
    private Unique unique;

    @BeforeEach
    void open(BrowserPool browsers, UiSettings settings, Cleanup cleanup, Unique unique) {
        this.unique = unique;
        this.browserPage = cleanup.own(new UiSession(browsers, BrowserProfiles.get("mobile"), settings)).page();
        this.page = new UsersPage(browserPage).open();
    }

    @Override
    public UsersScreen screen() {
        return page;
    }

    @Override
    public Unique unique() {
        return unique;
    }

    @Test
    void formIsCollapsedAndRowsAreCards() {
        ShownUser user = page.open().create(aUser());   // works although the form starts collapsed

        page.open();
        assertThat(page.isFormExpanded()).isFalse();
        assertThat(browserPage.locator("[data-user-id='" + user.id() + "']").evaluate("row => getComputedStyle(row).display"))
                .isEqualTo("block");
    }
}
