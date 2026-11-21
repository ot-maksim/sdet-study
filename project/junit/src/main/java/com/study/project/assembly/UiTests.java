package com.study.project.assembly;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;

/** UI tests: the users page in a browser (project.ui.profile), one leased browser per test. Run only these with {@code -Dgroups=ui}. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("ui")
@ProjectTest
public @interface UiTests {
}
