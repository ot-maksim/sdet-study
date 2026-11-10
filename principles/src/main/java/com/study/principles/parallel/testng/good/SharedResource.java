package com.study.principles.parallel.testng.good;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * TestNG has no {@code @ResourceLock}; this is a hand-made one, enforced by {@link ShopNgTestBase#run}.
 * {@link Mode#READ_WRITE} for tests that change the shared thing, {@link Mode#READ} for tests that depend on it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SharedResource {

    String value();

    Mode mode() default Mode.READ_WRITE;

    enum Mode { READ, READ_WRITE }
}
