package com.study.principles.inheritance.session;

public class FakePage {
    private final FakeBrowser browser;
    private String url = "about:blank";

    FakePage(FakeBrowser browser) {
        this.browser = browser;
    }

    public void navigate(String url) {
        // Unlike the session, the fake page does NOT protect itself — so a bad session lets you "use" a dead browser
        this.url = url;
    }

    public String url() {
        return url;
    }

    public boolean isAlive() {
        return browser.isOpen();
    }
}
