package com.study.principles.inheritance.basetest.good.mixins;

import com.study.principles.inheritance.basetest.Browser;
import com.study.principles.inheritance.basetest.User;
import com.study.principles.inheritance.basetest.good.UiTest;

/** Test interface for UI tests; meta-annotations work on interfaces too. */
@UiTest
public interface UiAssertions {

    default void assertGreets(Browser browser, User user) {
        String greeting = browser.greeting();
        if (!greeting.equals("Hello, " + user.name())) {
            throw new AssertionError("expected greeting for " + user.name() + " but was: " + greeting);
        }
    }
}
