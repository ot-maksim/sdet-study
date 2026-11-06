package com.study.principles.invariants.leak;

import com.study.principles.invariants.leak.good.TestSuiteResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests that encapsulation of the good {@link TestSuiteResult} cannot be bypassed, and documents
 * (as an executable fact) that the bad version can.
 */
class TestSuiteResultTest {

    private static void assertInvariants(TestSuiteResult r) {
        assertThat(r.getFailedCount()).isEqualTo(r.getFailed().size());
    }

    @Test
    void externalCodeCannotModifyFailedList() {
        var result = new TestSuiteResult();
        result.recordFailure(new TestCase("loginTest", false));

        assertThatThrownBy(() -> result.getFailed().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertInvariants(result);
    }

    @Test
    void snapshotDoesNotChangeAfterNewFailures() {
        var result = new TestSuiteResult();
        result.recordFailure(new TestCase("loginTest", false));
        var snapshot = result.getFailed();

        result.recordFailure(new TestCase("searchTest", true));

        assertThat(snapshot).hasSize(1);
        assertInvariants(result);
    }

    @Test
    void rejectsNull() {
        var result = new TestSuiteResult();
        assertThatThrownBy(() -> result.recordFailure(null)).isInstanceOf(NullPointerException.class);
        assertInvariants(result);
    }

    @Test
    void badVersionCanBeCorruptedThroughGetter() {
        var bad = new com.study.principles.invariants.leak.bad.TestSuiteResult();
        bad.recordFailure(new TestCase("loginTest", false));

        bad.getFailed().clear();

        assertThat(bad.getFailedCount()).isNotEqualTo(bad.getFailed().size());
    }
}
