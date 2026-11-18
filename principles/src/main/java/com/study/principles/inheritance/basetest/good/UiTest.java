package com.study.principles.inheritance.basetest.good;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * UI tests need users from the API, a browser, and a screenshot on failure. {@code ApiExtension} is also in
 * {@link ApiTest}: JUnit registers a declaratively added extension class once, so stacking is safe.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith({ApiExtension.class, BrowserExtension.class, ScreenshotOnFailure.class})
public @interface UiTest {
}
