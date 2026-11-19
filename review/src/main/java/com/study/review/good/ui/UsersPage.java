package com.study.review.good.ui;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.study.review.good.api.NewUser;
import java.util.List;

/**
 * {@code /users}: actions and queries, no assertions and no sleeps. Each action waits for its own visible
 * outcome (Playwright auto-waits), so it takes as long as the page needs. A rejected submit is a result
 * ({@link #rejectedWith}), not a failure inside the page object. The page gets its {@link Page} from outside;
 * the base URL is set on the browser context. Full version with components: {@code framework/ui}.
 */
public final class UsersPage {

    private final Page page;

    public UsersPage(Page page) {
        this.page = page;
    }

    public UsersPage open() {
        page.navigate("/users");
        page.getByTestId("new-user-toggle").waitFor();
        return this;
    }

    public UsersPage create(NewUser user) {
        submit(user);
        page.getByTestId("notice").waitFor();
        return this;
    }

    /** Submits and returns the error code the form shows. */
    public String rejectedWith(NewUser user) {
        submit(user);
        Locator error = page.getByTestId("form-error");
        error.waitFor();
        return error.getAttribute("data-error-code");
    }

    public List<String> shownEmails() {
        return page.getByTestId("user-email").allTextContents().stream().map(String::strip).toList();
    }

    private void submit(NewUser user) {
        Locator form = page.getByTestId("new-user-form");
        if (!form.isVisible()) {
            page.getByTestId("new-user-toggle").click();
        }
        form.getByTestId("name-input").fill(user.name());
        form.getByTestId("email-input").fill(user.email());
        form.getByTestId("age-input").fill(user.age() == null ? "" : String.valueOf(user.age()));
        form.getByTestId("country-input").fill(user.country());
        form.getByTestId("create-user").click();
    }
}
