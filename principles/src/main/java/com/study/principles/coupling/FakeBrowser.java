package com.study.principles.coupling;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory browser over a tiny app: {@code /login} (fields {@code username}, {@code password}, button
 * {@code sign-in}) and {@code /dashboard} (element {@code greeting}). The session lives in the browser —
 * like a cookie — so a second browser is a second, anonymous user. Every call on a closed browser fails.
 */
public final class FakeBrowser implements Browser {

    private static final AtomicInteger COUNTER = new AtomicInteger();
    private static final Map<String, String> PASSWORDS = Map.of(
            "alice", "alice-pass",
            "bob", "bob-pass");

    private final String name = "browser-" + COUNTER.incrementAndGet();
    private final Map<String, String> fields = new HashMap<>();
    private String path = "about:blank";
    private String loggedInAs;
    private String error = "";
    private boolean closed;

    @Override
    public void open(String path) {
        ensureOpen();
        fields.clear();
        this.path = path.equals("/dashboard") && loggedInAs == null ? "/login" : path;   // redirect
    }

    @Override
    public void fill(String field, String value) {
        ensureOpen();
        fields.put(field, value);
    }

    @Override
    public void click(String button) {
        ensureOpen();
        if (!path.equals("/login") || !button.equals("sign-in")) {
            throw new IllegalStateException(name + ": no button '" + button + "' on " + path);
        }
        String user = fields.get("username");
        if (user != null && PASSWORDS.getOrDefault(user, "\0").equals(fields.get("password"))) {
            loggedInAs = user;
            error = "";
            open("/dashboard");
        } else {
            error = "Invalid credentials";
        }
    }

    @Override
    public String currentPath() {
        ensureOpen();
        return path;
    }

    @Override
    public String text(String id) {
        ensureOpen();
        if (path.equals("/dashboard") && id.equals("greeting")) return "Hello, " + loggedInAs;
        if (path.equals("/login") && id.equals("error")) return error;
        throw new IllegalStateException(name + ": no element '" + id + "' on " + path);
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
    }

    private void ensureOpen() {
        if (closed) throw new IllegalStateException(name + " is closed");
    }

    @Override
    public String toString() {
        return name + (closed ? " (closed)" : "");
    }
}
