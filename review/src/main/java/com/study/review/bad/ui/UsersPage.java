package com.study.review.bad.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Page;
import com.study.review.bad.api.UserApi;
import com.study.review.bad.config.FrameworkConfig;
import com.study.review.bad.core.Driver;
import java.util.Map;

/** Page object of {@code /users}. */
public class UsersPage {

    private static final String FORM = "[data-testid=new-user-form]";

    private final Page page = Driver.getPage();

    public UsersPage open() {
        page.navigate(FrameworkConfig.getInstance().get("base.url") + "/users");
        sleep(1000);
        return this;
    }

    public void createUser(String name, String email, int age, String country) {
        if (!page.isVisible(FORM)) {
            page.click("[data-testid=new-user-toggle]");
        }
        page.fill("[data-testid=name-input]", name);
        page.fill("[data-testid=email-input]", email);
        page.fill("[data-testid=age-input]", String.valueOf(age));
        page.fill("[data-testid=country-input]", country);
        page.click("[data-testid=create-user]");
        sleep(2000);
        assertThat(page.isVisible("[data-testid=notice]")).as("user is created").isTrue();
    }

    public void createUserViaApi(Map<String, Object> user) {
        new UserApi().createUser(user);
        page.reload();
        sleep(1000);
    }

    public void verifyUserInTable(String email) {
        assertThat(page.content()).contains(email);
    }

    public void verifyUserNotInTable(String email) {
        assertThat(page.content()).doesNotContain(email);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
