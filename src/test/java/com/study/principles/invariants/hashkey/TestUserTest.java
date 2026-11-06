package com.study.principles.invariants.hashkey;

import com.study.principles.invariants.hashkey.good.TestUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestUserTest {

    @Test
    void immutableKeyStaysReachableAfterRename() {
        var tokens = new HashMap<TestUser, String>();
        var user = new TestUser("alice");
        tokens.put(user, "token-123");

        var renamed = user.withLogin("alice_renamed");

        assertThat(tokens).containsEntry(user, "token-123");
        assertThat(tokens).doesNotContainKey(renamed);
        assertThat(user.login()).isEqualTo("alice");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t"})
    void rejectsBlankLogin(String login) {
        assertThatThrownBy(() -> new TestUser(login)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullLogin() {
        assertThatThrownBy(() -> new TestUser(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void mutableKeyBecomesUnreachable() {
        var tokens = new HashMap<com.study.principles.invariants.hashkey.bad.TestUser, String>();
        var user = new com.study.principles.invariants.hashkey.bad.TestUser("alice");
        tokens.put(user, "token-123");

        user.setLogin("alice_renamed");

        assertThat(tokens.get(user)).isNull();
        assertThat(tokens).hasSize(1);
    }
}
