package com.study.patterns.builder.inheritance;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for the module test stack (JUnit 5 + AssertJ)
 * and for the self-typed builder hierarchy: shared setters return the concrete builder type.
 */
class BuilderInheritanceTest {

    @Test
    void userBuilderSetsSharedFields() {
        User user = User.builder().name("bob").surName("old").build();

        assertThat(user.getName()).isEqualTo("bob");
        assertThat(user.getSurName()).isEqualTo("old");
    }

    @Test
    void adminBuilderKeepsItsTypeAfterSharedSetters() {
        // name() is declared in the base Builder, yet returns AdminBuilder, so level() is reachable
        Admin admin = Admin.adminBuilder().name("tom").surName("very old").level(5).build();

        assertThat(admin)
                .isInstanceOf(User.class)
                .extracting(User::getName, User::getSurName, Admin::getLevel)
                .containsExactly("tom", "very old", 5);
    }
}
