package com.study.framework.tests;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * UI tests: the users page in a browser (profile ui.profile), one leased browser per test. Wires the class to {@link SutComposition}; run only these with {@code -Dgroups=ui}.
 * A kind is an annotation, not a base class: no inherited state, and a class can only be one kind on purpose.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Tag("ui")
@ExtendWith(SutComposition.class)
public @interface UiTests {
}
