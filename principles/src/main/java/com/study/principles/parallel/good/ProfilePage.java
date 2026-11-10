package com.study.principles.parallel.good;

import com.study.principles.parallel.FakeWebDriver;

/**
 * Page object: per test, because the driver it wraps is per test. It holds nothing but that driver, so
 * it costs nothing to create, and {@link ShopExtension} builds it over the <b>same</b> driver the test
 * (and its {@code @BeforeEach}) gets. The whole object graph of a test (driver → pages → steps) is
 * created per test; nothing in it is static.
 *
 * <p>The opposite: page objects as static singletons ({@code Pages.profile()}), or page objects that
 * fetch the driver from a static holder. Both share the session through the back door.
 */
public final class ProfilePage {

    private final FakeWebDriver driver;

    public ProfilePage(FakeWebDriver driver) {
        this.driver = driver;
    }

    /** The usual speed-up: a session from the API, put into the browser as a cookie. */
    public ProfilePage openWithSession(String sessionToken) {
        driver.addCookie("session", sessionToken);
        return open();
    }

    public ProfilePage open() {
        driver.get("/profile");
        return this;
    }

    public boolean isOpen() {
        return "/profile".equals(driver.getCurrentUrl());
    }

    public String title() {
        return driver.findText("title");
    }

    public String greeting() {
        return driver.findText("greeting");
    }

    public String plan() {
        return driver.findText("plan");
    }
}
