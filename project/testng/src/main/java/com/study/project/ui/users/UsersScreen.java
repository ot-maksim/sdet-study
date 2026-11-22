package com.study.project.ui.users;

import java.util.Optional;

/**
 * The users screen as a person uses it, independent of how it is driven. Today: {@link UsersPage} (web and
 * mobile web via Playwright). A native app would get its own implementation (Appium) passing the same
 * contract test — not a stub that throws {@code UnsupportedOperationException} (principles/lsp, isp).
 *
 * <p>Methods find users by email, never by position or count: other tests create users on the same stand
 * at the same time.
 */
public interface UsersScreen {

    UsersScreen open();

    /** Fills and submits the form; returns the user as listed afterwards. */
    ShownUser create(UserForm user);

    /** Fills and submits a form the SUT must reject; returns the shown error. */
    FormError createRejected(UserForm user);

    Optional<ShownUser> find(String email);

    void delete(String email);

    /** The confirmation after the last action ("User created"), if shown. */
    Optional<String> notice();
}
