package com.study.project.assembly;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;

/** E2E tests: one business flow across API, database and page. Run only these with {@code -Dgroups=e2e}. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("e2e")
@ProjectTest
public @interface E2eTests {
}
