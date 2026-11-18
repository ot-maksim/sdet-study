package com.study.principles.coupling.bad.locator;

/**
 * The constructor says "I need nothing". The truth is in the method bodies: {@code DriverManager.get()},
 * set by somebody, somewhere, before — temporal coupling with a global.
 */
public class LoginPage {

    public void open() {
        DriverManager.get().open("/login");
    }

    public void loginAs(String user, String password) {
        DriverManager.get().fill("username", user);
        DriverManager.get().fill("password", password);
        DriverManager.get().click("sign-in");
    }
}
