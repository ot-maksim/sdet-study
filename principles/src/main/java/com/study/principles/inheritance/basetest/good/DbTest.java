package com.study.principles.inheritance.basetest.good;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Adds a DB connection. {@code @UiTest @DbTest} is the "UiDbBaseTest" — without a new class. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(DbExtension.class)
public @interface DbTest {
}
