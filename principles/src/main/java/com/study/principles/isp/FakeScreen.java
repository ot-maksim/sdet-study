package com.study.principles.isp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * The app as both platforms see it: a login form ({@code username}, {@code password}, {@code sign-in}),
 * a {@code greeting}, and a {@code slide} carousel. Web and native address elements by the same ids
 * (test ids / accessibility ids), so one page object can drive both — if its dependency allows it.
 */
public final class FakeScreen {

    private static final Map<String, String> PASSWORDS = Map.of("alice", "alice-pass", "bob", "bob-pass");
    private static final List<String> SLIDES = List.of("Welcome", "Track orders", "Get started");

    private final Map<String, String> fields = new HashMap<>();
    private final List<String> screenshots = new ArrayList<>();
    private String user;
    private int slide;

    public void fill(String id, String value) {
        if (!id.equals("username") && !id.equals("password")) throw missing(id);
        fields.put(id, value);
    }

    public void press(String id) {
        if (!id.equals("sign-in")) throw missing(id);
        String name = fields.get("username");
        if (name != null && PASSWORDS.get(name) != null && PASSWORDS.get(name).equals(fields.get("password"))) user = name;
    }

    public String read(String id) {
        return switch (id) {
            case "greeting" -> user == null ? "Please sign in" : "Hello, " + user;
            case "slide" -> SLIDES.get(slide);
            default -> throw missing(id);
        };
    }

    public void nextSlide() {
        slide = Math.min(slide + 1, SLIDES.size() - 1);
    }

    /** Session injected from outside the UI (a cookie, a deep link token). */
    public void restoreSession(String user) {
        if (!PASSWORDS.containsKey(user)) throw new IllegalArgumentException("unknown user " + user);
        this.user = user;
    }

    public String screenshot(String name) {
        screenshots.add(name);
        return "screenshots/" + name + ".png";
    }

    public List<String> screenshots() {
        return List.copyOf(screenshots);
    }

    private static NoSuchElementException missing(String id) {
        return new NoSuchElementException("no element '" + id + "'");
    }
}
