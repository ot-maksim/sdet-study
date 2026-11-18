package com.study.principles.inheritance.pages;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * In-memory app with three pages that share <i>some</i> widgets — the real-life shape that breaks page
 * hierarchies:
 * <pre>
 *   page         header   sidebar   search
 *   /dashboard     +         +         +
 *   /reports       +         +
 *   /settings      +                   +
 * </pre>
 * On {@link Device#MOBILE} the header collapses: user name and logout are behind {@code #burger}.
 * Acting on an element that is not on the current screen fails like a real locator timeout.
 */
public final class FakeBrowser {

    private static final Set<String> WITH_SIDEBAR = Set.of("/dashboard", "/reports");
    private static final Set<String> WITH_SEARCH = Set.of("/dashboard", "/settings");
    private static final List<String> CATALOG = List.of("monthly report", "weekly report", "user settings");

    private final Device device;
    private final String user;
    private String path = "about:blank";
    private boolean menuOpen;
    private String query = "";

    public FakeBrowser(Device device, String user) {
        this.device = device;
        this.user = user;
    }

    public Device device() {
        return device;
    }

    public void open(String path) {
        this.path = path;
        menuOpen = false;
        query = "";
    }

    public String currentPath() {
        return path;
    }

    public void click(String selector) {
        ensureVisible(selector);
        switch (selector) {
            case "#burger" -> menuOpen = !menuOpen;
            case "#logout" -> open("/login");
            case "#nav-dashboard" -> open("/dashboard");
            case "#nav-reports" -> open("/reports");
            case "#search-go" -> { /* results are computed on read */ }
            default -> throw new IllegalArgumentException("not clickable: " + selector);
        }
    }

    public void type(String selector, String text) {
        ensureVisible(selector);
        query = text;
    }

    public String text(String selector) {
        ensureVisible(selector);
        return switch (selector) {
            case "#user-name" -> user;
            case "#results" -> String.join(", ", CATALOG.stream().filter(item -> item.contains(query)).toList());
            default -> throw new IllegalArgumentException("no text: " + selector);
        };
    }

    public List<String> visibleElements() {
        List<String> visible = new ArrayList<>();
        if (path.equals("/login")) return visible;
        if (device == Device.DESKTOP || menuOpen) visible.addAll(List.of("#user-name", "#logout"));
        if (device == Device.MOBILE) visible.add("#burger");
        if (WITH_SIDEBAR.contains(path)) visible.addAll(List.of("#nav-dashboard", "#nav-reports"));
        if (WITH_SEARCH.contains(path)) visible.addAll(List.of("#search", "#search-go", "#results"));
        return visible;
    }

    private void ensureVisible(String selector) {
        if (!visibleElements().contains(selector)) {
            throw new NoSuchElementException(selector + " is not visible on " + path + " (" + device + ")");
        }
    }
}
