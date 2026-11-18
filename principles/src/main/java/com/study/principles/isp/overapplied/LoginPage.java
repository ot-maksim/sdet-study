package com.study.principles.isp.overapplied;

/**
 * ISP over-applied: an interface per method. Wiring is cheap with method references
 * ({@code new LoginPage(browser::click, browser::type, browser::text)}), but the concept "a screen I act on"
 * has no name any more, every client lists its micro-dependencies, and a page that starts to need one more
 * method changes its constructor — and every place that builds it. Clients always use these three together:
 * they are one role.
 */
public final class LoginPage {

    private final Clicker clicker;
    private final Typer typer;
    private final TextReader reader;

    public LoginPage(Clicker clicker, Typer typer, TextReader reader) {
        this.clicker = clicker;
        this.typer = typer;
        this.reader = reader;
    }

    public void loginAs(String user, String password) {
        typer.type("username", user);
        typer.type("password", password);
        clicker.click("sign-in");
    }

    public String greeting() {
        return reader.text("greeting");
    }
}
