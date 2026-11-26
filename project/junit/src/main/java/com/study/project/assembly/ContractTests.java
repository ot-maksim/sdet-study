package com.study.project.assembly;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;

/**
 * Contract tests: one response of the SUT per test, checked against {@code openapi.yaml} and nothing else (ADR 0015).
 * Run only these with {@code -Dgroups=contract}; the functional kinds do not fail on the contract.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("contract")
@ProjectTest
public @interface ContractTests {
}
