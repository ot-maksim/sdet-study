package com.study.framework.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.study.framework.api.users.User;
import com.study.framework.db.users.UserRow;
import com.study.framework.db.users.UserRows;
import com.study.framework.ui.users.ShownUser;
import com.study.framework.ui.users.UsersScreen;

/**
 * Checks that relate the channels' views of one user — the only place that knows the API's {@code User}, the
 * table's {@code UserRow} and the page's {@code ShownUser} describe the same thing. Stateless, run-scoped.
 */
public final class UserChecks {

    private final UserRows rows;

    public UserChecks(UserRows rows) {
        this.rows = rows;
    }

    /** The table holds exactly what the API returned (normalization, timestamp precision). */
    public UserRow assertStoredAsReturned(User user) {
        UserRow row = rows.findById(user.id()).orElseThrow(() -> new AssertionError("No row for " + user));
        assertThat(new User(row.id(), row.name(), row.email(), row.age(), row.country(), row.createdAt()))
                .as("row of user %s", user.id())
                .isEqualTo(user);
        return row;
    }

    /** The page lists the user with the same data the API returned. */
    public ShownUser assertListed(UsersScreen screen, User user) {
        ShownUser shown = screen.open().find(user.email())
                .orElseThrow(() -> new AssertionError("User " + user.email() + " is not listed on the page"));
        assertThat(shown).as("listed user %s", user.email())
                .isEqualTo(new ShownUser(user.id(), user.name(), user.email(), user.age(), user.country()));
        return shown;
    }

    public void assertNotStored(String email) {
        assertThat(rows.findByEmail(email)).as("row with email %s", email).isEmpty();
    }
}
