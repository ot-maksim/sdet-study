package com.study.framework.core.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScopeTest {

    private final List<String> log = new ArrayList<>();

    @Test
    void undoesInReverseOrder() {
        Scope scope = new Scope("test");
        scope.defer("delete user", () -> log.add("delete user"));
        scope.defer("delete order of user", () -> log.add("delete order"));
        scope.own(() -> log.add("close page"));

        scope.close();

        assertThat(log).containsExactly("close page", "delete order", "delete user");
    }

    @Test
    void runsEveryActionAndReportsAllFailures() {
        Scope scope = new Scope("creates two users");
        scope.defer("delete user 1", () -> log.add("1"));
        scope.defer("delete user 2", () -> {
            throw new IllegalStateException("404 user_not_found");
        });
        scope.defer("delete user 3", () -> {
            throw new IllegalStateException("connection refused");
        });

        assertThatThrownBy(scope::close)
                .isInstanceOf(CleanupFailedException.class)
                .hasMessageContaining("2 of 3 cleanup actions failed in 'creates two users'")
                .hasMessageContaining("delete user 3: java.lang.IllegalStateException: connection refused")
                .hasMessageContaining("delete user 2: java.lang.IllegalStateException: 404 user_not_found")
                .satisfies(e -> assertThat(e.getSuppressed()).hasSize(2));
        assertThat(log).containsExactly("1");
    }

    @Test
    void refusesLateRegistrationAndClosesOnce() {
        Scope scope = new Scope("test");
        scope.defer("once", () -> log.add("once"));
        scope.close();
        scope.close();

        assertThat(log).containsExactly("once");
        assertThatThrownBy(() -> scope.defer("too late", () -> log.add("never")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("'too late' would never run");
    }
}
