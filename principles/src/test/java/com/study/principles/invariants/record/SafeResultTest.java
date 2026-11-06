package com.study.principles.invariants.record;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SafeResultTest {

    @Test
    void isDisconnectedFromSourceList() {
        List<String> source = new ArrayList<>(List.of("loginTest"));
        var result = new SafeResult(source);

        source.add("searchTest");

        assertThat(result.failedTests()).containsExactly("loginTest");
    }

    @Test
    void accessorIsReadOnly() {
        var result = new SafeResult(List.of("loginTest"));
        assertThatThrownBy(() -> result.failedTests().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shallowResultChangesWithSourceList() {
        List<String> source = new ArrayList<>(List.of("loginTest"));
        var result = new ShallowResult(source);

        source.add("searchTest");

        assertThat(result.failedTests()).hasSize(2);   // the trap, as an executable fact
    }
}
