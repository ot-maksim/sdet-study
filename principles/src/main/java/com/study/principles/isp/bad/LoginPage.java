package com.study.principles.isp.bad;

/**
 * Shared by web and native tests — on paper. It depends on all of {@link Driver}, and it uses
 * {@code open(url)}, which only web supports: on native it fails at run time. Nothing in the signature
 * warned anybody.
 */
public final class LoginPage {

    private final Driver driver;

    public LoginPage(Driver driver) {
        this.driver = driver;
    }

    public LoginPage open() {
        driver.open("/login");
        return this;
    }

    public void loginAs(String user, String password) {
        driver.type("username", user);
        driver.type("password", password);
        driver.click("sign-in");
    }

    public String greeting() {
        return driver.text("greeting");
    }
}
