package com.study.principles.inheritance.basetest.good;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Meta-annotation: what an API test may ask for. Composes with {@link UiTest} and {@link DbTest}. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(ApiExtension.class)
public @interface ApiTest {
}
