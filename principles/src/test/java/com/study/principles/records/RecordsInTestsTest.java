package com.study.principles.records;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Records inside test code itself: named parameter sets and ad-hoc rows for comparison.
 * The "SUT" here is a tiny fake login form and a fake UI table.
 */
class RecordsInTestsTest {

    /**
     * One case of a data-driven test. Compared to {@code Arguments.of("", "pwd", "Login is required")}:
     * named fields, type-checked, and {@code toString} gives a readable display name in the report.
     * (Contains a password — fine for fake test data; for real secrets see {@code traps.good.Credentials}.)
     */
    record LoginCase(String login, String password, String expectedError) {
    }

    static Stream<LoginCase> invalidLogins() {
        return Stream.of(
                new LoginCase("", "pwd", "Login is required"),
                new LoginCase("john", "", "Password is required"),
                new LoginCase("john", "wrong", "Invalid credentials"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidLogins")
    void showsErrorForInvalidLogin(LoginCase c) {
        assertThat(FakeLoginForm.submit(c.login(), c.password())).isEqualTo(c.expectedError());
    }

    @Test
    void comparesUiTableRowsAsValues() {
        // A local record: a throwaway type scoped to one test, no file, no equals to write.
        record Row(String login, String role) {
        }

        List<Row> actual = FakeUsersTable.rows().stream()
                .map(cells -> new Row(cells.get(0), cells.get(2)))   // ignore the volatile "created" column
                .toList();

        assertThat(actual).containsExactlyInAnyOrder(
                new Row("john", "ADMIN"),
                new Row("jane", "USER"));
    }

    private static final class FakeLoginForm {
        static String submit(String login, String password) {
            if (login.isBlank()) return "Login is required";
            if (password.isBlank()) return "Password is required";
            return "john".equals(login) && "s3cret".equals(password) ? "" : "Invalid credentials";
        }
    }

    private static final class FakeUsersTable {
        /** Cells as a UI driver would read them: login, created, role. */
        static List<List<String>> rows() {
            return List.of(
                    List.of("jane", "2026-11-08 10:15:02", "USER"),
                    List.of("john", "2026-11-08 10:15:01", "ADMIN"));
        }
    }
}
