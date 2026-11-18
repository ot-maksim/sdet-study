package com.study.principles.inheritance.basetest;

/** Fake browser: the expensive resource (seconds to launch, a grid slot). Every call on a closed browser fails. */
public final class Browser implements AutoCloseable {

    private User user;
    private boolean closed;

    private Browser() {
    }

    public static Browser launch() {
        Startups.record("browser launched");
        return new Browser();
    }

    public void loginAs(User user) {
        ensureOpen();
        this.user = user;
    }

    public String greeting() {
        ensureOpen();
        return user == null ? "Please log in" : "Hello, " + user.name();
    }

    public void screenshot(String name) {
        ensureOpen();
        Startups.record("screenshot " + name);
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        Startups.record("browser closed");
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException("browser is closed");
    }
}
