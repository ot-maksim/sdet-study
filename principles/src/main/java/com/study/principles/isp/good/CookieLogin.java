package com.study.principles.isp.good;

/**
 * Needs two roles of one object: an <b>intersection type</b>. {@code CookieLogin.loginAs(device, ..)} does not
 * compile — and the client still does not depend on screenshots or interaction.
 */
public final class CookieLogin {

    private CookieLogin() {
    }

    public static <B extends Cookies & Navigation> void loginAs(B browser, String user) {
        browser.addCookie("session", user);
        browser.open("/home");
    }
}
