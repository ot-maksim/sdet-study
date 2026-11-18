package com.study.principles.inheritance.basetest.bad.split;

import com.study.principles.inheritance.basetest.Browser;
import com.study.principles.inheritance.basetest.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/** Extends {@link ApiBaseTest} because the UI needs a user created through the API. */
public abstract class UiBaseTest extends ApiBaseTest {

    protected Browser browser;
    protected User user;

    @BeforeEach
    void uiSetUp() {
        browser = Browser.launch();
        user = api.createUser("ui-user");
        browser.loginAs(user);
    }

    @AfterEach
    void uiTearDown() {
        browser.close();
    }
}
