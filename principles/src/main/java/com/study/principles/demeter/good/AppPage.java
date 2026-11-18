package com.study.principles.demeter.good;

import com.study.principles.demeter.FakeApp;

/**
 * "Tell, don't ask": the page offers intentions — {@code logout()}, {@code switchLanguage(..)} — and keeps
 * its components as private composition. Tests depend on what the user does, not on where things are.
 * <p>
 * The V2 redesign (user menu moved to the sidebar) was a change in {@link #userMenu()} only.
 */
public final class AppPage {

    private static final String HEADER_MENU = "header > user-menu";
    private static final String SIDEBAR_MENU = "sidebar > user-menu";

    private final FakeApp app;

    public AppPage(FakeApp app) {
        this.app = app;
    }

    public void logout() {
        userMenu().select("Logout");
    }

    public void switchLanguage(String language) {
        userMenu().select("Language: " + language);
    }

    public void openProfile() {
        userMenu().select("Profile");
    }

    /** The only place that knows where the user menu lives. */
    private UserMenu userMenu() {
        return new UserMenu(app, app.isVisible(HEADER_MENU) ? HEADER_MENU : SIDEBAR_MENU);
    }
}
