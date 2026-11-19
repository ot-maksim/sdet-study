package com.study.template;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;

/** API tests: the SUT through its REST API, checked against the database. Run only these with {@code -Dgroups=api}. Same name as in framework/tests: a test moves by its import. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("api")
@TemplateTest
public @interface ApiTests {
}
