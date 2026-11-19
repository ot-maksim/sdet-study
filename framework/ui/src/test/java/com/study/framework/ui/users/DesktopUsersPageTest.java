package com.study.framework.ui.users;

import static org.assertj.core.api.Assertions.assertThat;

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

@ExtendWith(UiTestRoot.class)
class DesktopUsersPageTest implements UsersScreenContract {

    private UsersPage page;
    private Unique unique;

    @BeforeEach
    void open(BrowserPool browsers, UiSettings settings, Cleanup cleanup, Unique unique) {
        this.unique = unique;
        this.page = new UsersPage(cleanup.own(new UiSession(browsers, BrowserProfiles.get("desktop"), settings)).page()).open();
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
    void formIsOpenOnDesktop() {
        assertThat(page.isFormExpanded()).isTrue();
    }
}
