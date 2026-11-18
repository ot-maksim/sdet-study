package com.study.principles.demeter;

import java.util.NoSuchElementException;

/**
 * In-memory app with a user menu (Profile, Language: xx, Logout), addressed by selectors like a browser.
 * <ul>
 *   <li>{@link Layout#V1}: the menu lives in the header — {@code "header > user-menu"};</li>
 *   <li>{@link Layout#V2}: the redesign moved it to the sidebar — {@code "sidebar > user-menu"}.</li>
 * </ul>
 * Items are clickable only while the menu is open: {@code "<menu> > item:Logout"}.
 */
public final class FakeApp {

    public enum Layout {
        V1("header > user-menu"),
        V2("sidebar > user-menu");

        private final String userMenu;

        Layout(String userMenu) {
            this.userMenu = userMenu;
        }
    }

    private final Layout layout;
    private boolean loggedIn = true;
    private boolean menuOpen;
    private String language = "en";
    private String path = "/home";

    public FakeApp(Layout layout) {
        this.layout = layout;
    }

    public void click(String selector) {
        if (!loggedIn) throw new NoSuchElementException("no element '" + selector + "' on " + path);
        if (selector.equals(layout.userMenu)) {
            menuOpen = true;
            return;
        }
        String itemPrefix = layout.userMenu + " > item:";
        if (menuOpen && selector.startsWith(itemPrefix)) {
            menuOpen = false;
            choose(selector.substring(itemPrefix.length()));
            return;
        }
        throw new NoSuchElementException("no element '" + selector + "' (layout " + layout + ")");
    }

    public boolean isVisible(String selector) {
        return loggedIn && selector.equals(layout.userMenu);
    }

    public boolean loggedIn() {
        return loggedIn;
    }

    public String language() {
        return language;
    }

    public String path() {
        return path;
    }

    private void choose(String item) {
        if (item.equals("Profile")) {
            path = "/profile";
        } else if (item.equals("Logout")) {
            loggedIn = false;
            path = "/login";
        } else if (item.startsWith("Language: ")) {
            language = item.substring("Language: ".length());
        } else {
            throw new NoSuchElementException("no menu item '" + item + "'");
        }
    }
}
