package com.study.template;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;

/** UI tests: the users page in a browser (template.ui.profile), one leased browser per test. Run only these with {@code -Dgroups=ui}. Same name as in framework/tests: a test moves by its import. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("ui")
@TemplateTest
public @interface UiTests {
}
