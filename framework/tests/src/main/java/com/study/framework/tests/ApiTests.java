package com.study.framework.tests;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * API tests: the SUT through its REST API, checked against the database. Wires the class to {@link SutComposition}; run only these with {@code -Dgroups=api}.
 * A kind is an annotation, not a base class: no inherited state, and a class can only be one kind on purpose.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("api")
@ExtendWith(SutComposition.class)
public @interface ApiTests {
}
