package com.study.principles.invariants.testrun;

import java.util.Objects;

/**
 * Result of a single test. Immutable value object.
 *
 * @param name   test name, not {@code null}
 * @param passed whether the test passed
 */
public record TestResult(String name, boolean passed) {

    public TestResult {
        Objects.requireNonNull(name, "name");
    }

    public static TestResult passed(String name) {
        return new TestResult(name, true);
    }

    public static TestResult failed(String name) {
        return new TestResult(name, false);
    }
}
