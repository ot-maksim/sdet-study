package com.study.principles.isp.good;

/**
 * Depends on {@link Interaction} only, so it really is shared by web and native. Getting <i>to</i> the
 * screen is the caller's business: {@code browser.open("/login")} or {@code device.launchApp(..)}.
 */
public final class LoginPage {

    private final Interaction screen;

    public LoginPage(Interaction screen) {
        this.screen = screen;
    }

    public void loginAs(String user, String password) {
        screen.type("username", user);
        screen.type("password", password);
        screen.click("sign-in");
    }

    public String greeting() {
        return screen.text("greeting");
    }
}
