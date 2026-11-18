package com.study.principles.isp.bad;

/** Logs in without the UI by planting a session cookie. Accepts a native driver too — and fails at run time. */
public final class CookieLogin {

    private CookieLogin() {
    }

    public static void loginAs(Driver driver, String user) {
        driver.addCookie("session", user);
        driver.open("/home");
    }
}
