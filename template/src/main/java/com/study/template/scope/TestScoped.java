package com.study.template.scope;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.context.annotation.Scope;

/** One instance per test method, destroyed after it (also on failure); stage 2's {@code Bindings.test}. */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Scope(TestScope.NAME)
public @interface TestScoped {
}
