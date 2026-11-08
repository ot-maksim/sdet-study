package com.study.principles.records;

import com.study.principles.records.fit.bad.UserResponse;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The assertion every API test makes: {@code assertThat(actual).isEqualTo(expected)}. */
class UserResponseEqualityTest {

    @Test
    void badDtoGivesFalsePositive() {
        var expected = new UserResponse(1, "john", "j@x.io", "ADMIN");
        var actual = new UserResponse(1, "john", "j@x.io", "USER");

        assertThat(actual).isEqualTo(expected);   // passes — the role bug is not caught
    }

    @Test
    void badDtoExpectedValueCanChangeInsideASet() {
        var expected = new UserResponse(1, "john", "j@x.io", "ADMIN");
        var expectedUsers = new HashSet<UserResponse>();
        expectedUsers.add(expected);

        expected.setLogin("jane");                 // a "harmless" tweak for the next test case

        assertThat(expectedUsers.contains(expected)).isFalse();
    }

    @Test
    void recordDtoCatchesTheDifference() {
        var expected = new com.study.principles.records.fit.good.UserResponse(1, "john", "j@x.io", "ADMIN");
        var actual = new com.study.principles.records.fit.good.UserResponse(1, "john", "j@x.io", "USER");

        assertThatThrownBy(() -> assertThat(actual).isEqualTo(expected))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("role=USER")
                .hasMessageContaining("role=ADMIN");
    }
}
