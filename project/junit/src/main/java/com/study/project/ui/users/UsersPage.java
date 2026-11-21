package com.study.project.ui.users;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import java.util.Optional;

/**
 * {@code /users} driven through Playwright. Composed of components ({@link NewUserForm}, {@link UsersList}),
 * not inherited from a base page (principles/inheritance). Locators are {@code data-testid}s — the page's
 * agreed test interface. Every action ends by waiting for its visible outcome (the notice, the row), so callers
 * never need sleeps.
 */
public final class UsersPage implements UsersScreen {

    private final Page page;
    private final NewUserForm form;
    private final UsersList list;

    public UsersPage(Page page) {
        this.page = page;
        this.form = new NewUserForm(page);
        this.list = new UsersList(page);
    }

    @Override
    public UsersPage open() {
        page.navigate("/users");
        // Readiness = an element visible in every layout and with any data. Not the table: on a phone with no
        // users it has zero height (header hidden, no rows) and counts as invisible — found by the parallel test
        page.getByTestId("new-user-toggle").waitFor();
        return this;
    }

    @Override
    public ShownUser create(UserForm user) {
        form.fillAndSubmit(user);
        waitForNotice("User created");
        // The SUT normalizes the email; the page shows it lower-cased
        return list.waitFor(user.email().strip().toLowerCase());
    }

    @Override
    public FormError createRejected(UserForm user) {
        form.fillAndSubmit(user);
        return form.error();
    }

    @Override
    public Optional<ShownUser> find(String email) {
        return list.find(email);
    }

    @Override
    public void delete(String email) {
        list.row(email).getByTestId("delete-user").click();
        waitForNotice("User deleted");
        list.waitUntilGone(email);
    }

    @Override
    public Optional<String> notice() {
        Locator notice = page.getByTestId("notice");
        return notice.count() == 0 ? Optional.empty() : Optional.of(notice.textContent());
    }

    /** Collapsed behind a toggle on narrow screens; exposed for layout tests, not needed by scenarios. */
    public boolean isFormExpanded() {
        return form.isExpanded();
    }

    private void waitForNotice(String text) {
        page.getByTestId("notice").filter(new Locator.FilterOptions().setHasText(text)).waitFor();
    }
}
