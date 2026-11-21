package com.study.project.domain;

import com.study.project.api.users.NewUser;
import com.study.project.api.users.User;
import com.study.project.core.lifecycle.Cleanup;
import com.study.project.db.users.NewUserRow;
import com.study.project.db.users.UserRow;
import com.study.project.db.users.UserRows;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/**
 * Straight into the table: fast, and able to create states the API makes slow (thousands of users, old
 * timestamps). The price: it bypasses the application, so it must reproduce what the SUT would store —
 * trimmed name, lower-cased email, upper-cased country, microsecond timestamp. That is a <b>copy of SUT
 * knowledge</b> (DRY says no); it is accepted because the shared contract test fails as soon as the copy drifts.
 * It does not validate: give it valid users, or it seeds what production can never contain.
 */
public final class DbUserPrecondition implements UserPrecondition {

    private final UserRows rows;
    private final Clock clock;
    private final Cleanup cleanup;

    public DbUserPrecondition(UserRows rows, Clock clock, Cleanup cleanup) {
        this.rows = rows;
        this.clock = clock;
        this.cleanup = cleanup;
    }

    @Override
    public User create(NewUser user) {
        UserRow row = rows.insert(new NewUserRow(
                user.name().strip(),
                user.email().strip().toLowerCase(Locale.ROOT),
                user.age(),
                user.country().strip().toUpperCase(Locale.ROOT),
                clock.instant().truncatedTo(ChronoUnit.MICROS)));
        cleanup.defer("delete user row " + row.id() + " (" + row.email() + ")", () -> rows.delete(row.id()));
        return new User(row.id(), row.name(), row.email(), row.age(), row.country(), row.createdAt());
    }
}
