package com.study.project.assembly;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;

/** API tests: the SUT through its REST API, checked against the database. Run only these with {@code -Dgroups=api}. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("api")
@ProjectTest
public @interface ApiTests {
}
