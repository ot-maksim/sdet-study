package com.study.sut.users;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UserRulesTest {

    @Test
    void normalizesAValidUser() {
        ValidUser user = UserRules.validate(new NewUser("  Ada  ", " Ada@Example.COM ", 18, "de"));

        assertThat(user).isEqualTo(new ValidUser("Ada", "ada@example.com", 18, "DE"));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(nullValues = "null", value = {
            "blank name,       '   ', a@b.io,  30,   DE, name_blank",
            "missing name,     null,  a@b.io,  30,   DE, name_blank",
            "no at sign,       Ada,   ab.io,   30,   DE, email_invalid",
            "no domain dot,    Ada,   a@b,     30,   DE, email_invalid",
            "missing age,      Ada,   a@b.io,  null, DE, age_missing",
            "17 is too young,  Ada,   a@b.io,  17,   DE, age_too_low",
            "151 is too old,   Ada,   a@b.io,  151,  DE, age_too_high",
            "not a country,    Ada,   a@b.io,  30,   XX, country_invalid",
            "alpha-3 country,  Ada,   a@b.io,  30,   DEU, country_invalid",
            "name before age,  '',    a@b.io,  17,   DE, name_blank",
    })
    void rejects(String why, String name, String email, Integer age, String country, String code) {
        assertThatThrownBy(() -> UserRules.validate(new NewUser(name, email, age, country)))
                .isInstanceOfSatisfying(UserRejectedException.class, e -> {
                    assertThat(e.code()).isEqualTo(code);
                    assertThat(e.reason()).isEqualTo(UserRejectedException.Reason.INVALID);
                });
    }

    @Test
    void boundariesAreInclusive() {
        assertThat(UserRules.validate(new NewUser("Ada", "a@b.io", 18, "DE")).age()).isEqualTo(18);
        assertThat(UserRules.validate(new NewUser("Ada", "a@b.io", 150, "DE")).age()).isEqualTo(150);
        assertThat(UserRules.validate(new NewUser("x".repeat(100), "a@b.io", 30, "DE")).name()).hasSize(100);
        assertThatThrownBy(() -> UserRules.validate(new NewUser("x".repeat(101), "a@b.io", 30, "DE")))
                .hasFieldOrPropertyWithValue("code", "name_too_long");
    }
}
