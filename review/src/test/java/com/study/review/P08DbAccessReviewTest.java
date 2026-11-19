package com.study.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.study.review.bad.db.DbUtils;
import com.study.review.good.GoodRoot;
import com.study.review.good.api.User;
import com.study.review.good.api.UsersClient;
import com.study.review.good.data.TestUsers;
import com.study.review.good.db.UserRow;
import com.study.review.good.db.UserRows;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * #8 — SQL by string concatenation, untyped rows, leaked statements. Needs the compose stand (Postgres and the
 * SUT on 7070): {@code docker compose up -d --wait}; skipped without it.
 */
class P08DbAccessReviewTest {

    private static final String DB_URL = "jdbc:postgresql://localhost:5432/sut";
    private static final URI COMPOSE_SUT = URI.create("http://localhost:7070");

    @BeforeAll
    static void needsTheComposeStand() {
        try (Connection ignored = DriverManager.getConnection(DB_URL, "sut", "sut")) {
            new UsersClient(COMPOSE_SUT, Duration.ofSeconds(2)).all();
        } catch (Exception e) {
            assumeTrue(false, "No compose stand (docker compose up -d --wait): " + e.getMessage());
        }
    }

    @Nested
    class Bad {

        @Test
        void aValidEmailWithAnApostropheBreaksTheQuery() throws SQLException {
            try (Connection db = DriverManager.getConnection(DB_URL, "sut", "sut")) {
                assertThatThrownBy(() -> DbUtils.findUserByEmail(db, "o'brien@example.com"))
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("Unterminated string literal");
            }
        }

        @Test
        void aCraftedValueMatchesARowThatIsNotThere() throws SQLException {
            try (Connection db = DriverManager.getConnection(DB_URL, "sut", "sut")) {
                assumeTrue(DbUtils.countUsers(db) > 0, "the table is empty");

                assertThat(DbUtils.findUserByEmail(db, "nobody@example.test' OR '1'='1"))
                        .as("\"found\" the first row of the table").isNotEmpty();
            }
        }
    }

    @Nested
    @ExtendWith(GoodRoot.class)
    class Good {

        @Test
        void anApostropheIsJustACharacter(UserRows rows, TestUsers data) {
            UsersClient composeSut = new UsersClient(COMPOSE_SUT, Duration.ofSeconds(5));
            User obrien = composeSut.create(data.aUser().withEmail("o'brien-" + data.email("x")));
            try {
                UserRow row = rows.findByEmail(obrien.email()).orElseThrow();

                assertThat(row.id()).isEqualTo(obrien.id());
                assertThat(row.country()).isEqualTo("GB");
                assertThat(rows.findByEmail("nobody@example.test' OR '1'='1")).isEmpty();
            } finally {
                composeSut.deleteIfExists(obrien.id());
            }
        }
    }
}
