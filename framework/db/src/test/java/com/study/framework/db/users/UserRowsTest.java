package com.study.framework.db.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.study.framework.core.data.Unique;
import com.study.framework.core.lifecycle.Scope;
import com.study.framework.db.Database;
import com.study.framework.db.DbException;
import com.study.framework.db.StandDatabase;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Against the compose stand's Postgres; every row it inserts is deleted after the test. */
@ExtendWith(StandDatabase.class)
class UserRowsTest {

    private static final Instant WITH_NANOS = Instant.parse("2026-11-19T10:15:30.123456789Z");

    private final Database db = StandDatabase.database();
    private final UserRows rows = new UserRows(db);
    private final Unique unique = new Unique();
    private final Scope cleanup = new Scope("UserRowsTest");

    @AfterEach
    void cleanUp() {
        cleanup.close();
    }

    private UserRow seed(NewUserRow row) {
        UserRow inserted = rows.insert(row);
        cleanup.defer("delete user row " + inserted.id(), () -> rows.delete(inserted.id()));
        return inserted;
    }

    private NewUserRow aRow() {
        return new NewUserRow("Ada", unique.email("ada"), 30, "DE", WITH_NANOS);
    }

    @Test
    void insertedRowIsFoundByIdAndEmail() {
        UserRow inserted = seed(aRow());

        assertThat(rows.findById(inserted.id())).contains(inserted);
        assertThat(rows.findByEmail(inserted.email())).contains(inserted);
        assertThat(inserted.country()).isEqualTo("DE");
    }

    @Test
    void postgresRoundsToMicrosecondsNotTruncates() {
        UserRow inserted = seed(aRow());

        // .123456789 -> .123457: rounded. Instant.truncatedTo(MICROS) on the test side would give .123456 and
        // miss by 1 µs; the SUT truncates *before* inserting, so for its rows both sides agree.
        assertThat(inserted.createdAt()).isEqualTo("2026-11-19T10:15:30.123457Z");
    }

    @Test
    void schemaConstraintsStillApplyToSeeding() {
        NewUserRow ada = aRow();
        seed(ada);

        assertThatThrownBy(() -> seed(ada)).isInstanceOf(DbException.class).hasMessageContaining("[23505]");
        assertThatThrownBy(() -> seed(new NewUserRow("Kid", unique.email("kid"), 17, "DE", WITH_NANOS)))
                .isInstanceOf(DbException.class).hasMessageContaining("[23514]");
    }

    @Test
    void seedingBypassesTheApplicationsNormalization() {
        // The SUT would store "ada-...@example.test"; the table accepts anything the constraints allow
        UserRow shouting = seed(new NewUserRow("Ada", unique.email("ADA"), 30, "XX", WITH_NANOS));

        assertThat(shouting.email()).startsWith("ADA-");
        assertThat(shouting.country()).isEqualTo("XX");
    }

    @Test
    void queryOneRefusesToPickTheFirstOfMany() {
        seed(aRow());
        seed(aRow());
        String marker = unique.runId();

        assertThatThrownBy(() -> db.queryOne("SELECT id FROM users WHERE email LIKE ?", rs -> rs.getLong(1), "ada-" + marker + "-%"))
                .isInstanceOf(DbException.class)
                .hasMessageContaining("Expected at most one row, got 2");
    }

    @Test
    void deleteIsIdempotent() {
        UserRow inserted = rows.insert(aRow());

        assertThat(rows.delete(inserted.id())).isTrue();
        assertThat(rows.delete(inserted.id())).isFalse();
        assertThat(rows.findById(inserted.id())).isEmpty();
    }
}
