package com.study.principles.invariants.leak;

/**
 * A failed test case as seen by a test report. Immutable value object.
 *
 * @param name  test name
 * @param flaky whether the test is known to be flaky
 */
public record TestCase(String name, boolean flaky) {
}
