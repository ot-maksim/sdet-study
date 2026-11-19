package com.study.framework.ui.users;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.util.Optional;
import java.util.regex.Pattern;

/** The list of users (rows on desktop, cards on a phone: same test ids), a component of {@link UsersPage}. */
final class UsersList {

    private final Page page;

    UsersList(Page page) {
        this.page = page;
    }

    /** The row whose email is exactly {@code email} — not a substring, not "the last row". */
    Locator row(String email) {
        Locator exactEmail = page.getByTestId("user-email")
                .filter(new Locator.FilterOptions().setHasText(Pattern.compile("^" + jsRegexLiteral(email) + "$")));
        return page.getByTestId("user-row").filter(new Locator.FilterOptions().setHas(exactEmail));
    }

    /**
     * Playwright evaluates the pattern in the browser, as a JavaScript RegExp: Java's {@code Pattern.quote}
     * ({@code \Q...\E}) means nothing there, so special characters are escaped one by one.
     */
    private static String jsRegexLiteral(String text) {
        return text.replaceAll("[.*+?^${}()|\\[\\]\\\\]", "\\\\$0");
    }

    Optional<ShownUser> find(String email) {
        Locator row = row(email);
        if (row.count() == 0) {
            return Optional.empty();
        }
        return Optional.of(read(row));
    }

    ShownUser waitFor(String email) {
        Locator row = row(email);
        row.waitFor();
        return read(row);
    }

    void waitUntilGone(String email) {
        row(email).waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.DETACHED));
    }

    private static ShownUser read(Locator row) {
        // textContent, not innerText: the phone layout prefixes cells with CSS ::before labels ("Name: ")
        return new ShownUser(
                Long.parseLong(row.getAttribute("data-user-id")),
                row.getByTestId("user-name").textContent(),
                row.getByTestId("user-email").textContent(),
                Integer.parseInt(row.getByTestId("user-age").textContent()),
                row.getByTestId("user-country").textContent());
    }
}
