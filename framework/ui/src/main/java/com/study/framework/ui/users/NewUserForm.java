package com.study.framework.ui.users;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

/**
 * The create form, a component of {@link UsersPage}. On a phone it is collapsed behind "New user"; the
 * component checks what the page shows and opens it if needed — no {@code if (mobile)}: the same code works
 * for any layout that hides the form, and a test does not lie when the breakpoint moves.
 */
final class NewUserForm {

    private final Page page;
    private final Locator form;

    NewUserForm(Page page) {
        this.page = page;
        this.form = page.getByTestId("new-user-form");
    }

    boolean isExpanded() {
        return form.isVisible();
    }

    void fillAndSubmit(UserForm user) {
        if (!isExpanded()) {
            page.getByTestId("new-user-toggle").click();
        }
        form.getByTestId("name-input").fill(user.name());
        form.getByTestId("email-input").fill(user.email());
        form.getByTestId("age-input").fill(user.age());
        form.getByTestId("country-input").fill(user.country());
        form.getByTestId("create-user").click();
    }

    FormError error() {
        Locator error = page.getByTestId("form-error");
        error.waitFor();   // auto-wait: appears after the round trip
        return new FormError(error.getAttribute("data-error-code"), error.textContent().strip());
    }
}
