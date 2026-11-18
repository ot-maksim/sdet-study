package com.study.principles.demeter.bad;

/**
 * What test bodies look like over {@link AppPage}: train wrecks. Each one encodes the whole path to the
 * element — and the order of steps ({@code open()} before {@code getDropdown()}). Three scenarios here;
 * in a real suite, every test that logs out or switches language. The V2 redesign breaks all of them.
 */
public final class UserScenarios {

    private UserScenarios() {
    }

    public static void logout(AppPage app) {
        app.getHeader().getUserMenu().open().getDropdown().getItem("Logout").click();
    }

    public static void switchLanguage(AppPage app, String language) {
        app.getHeader().getUserMenu().open().getDropdown().getItem("Language: " + language).click();
    }

    public static void openProfile(AppPage app) {
        app.getHeader().getUserMenu().open().getDropdown().getItem("Profile").click();
    }
}
