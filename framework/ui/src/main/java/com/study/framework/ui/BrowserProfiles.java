package com.study.framework.ui;

import com.microsoft.playwright.Browser;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Known profiles by name (OCP: a new profile is a new entry, no switch elsewhere — principles/ocp).
 * Mobile web is Chromium's device emulation: viewport, scale, touch, mobile user agent. Native mobile is
 * not a profile — it is another implementation of the screen interfaces (Appium, stage 4).
 */
public final class BrowserProfiles {

    private static final Map<String, BrowserProfile> PROFILES = new LinkedHashMap<>();

    static {
        add(new BrowserProfile("desktop", () -> new Browser.NewContextOptions().setViewportSize(1280, 800)));
        add(new BrowserProfile("mobile", () -> new Browser.NewContextOptions()
                .setViewportSize(390, 844)
                .setDeviceScaleFactor(3)
                .setIsMobile(true)
                .setHasTouch(true)
                .setUserAgent("Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/131.0.0.0 Mobile Safari/537.36")));
    }

    private BrowserProfiles() {
    }

    private static void add(BrowserProfile profile) {
        PROFILES.put(profile.name(), profile);
    }

    public static BrowserProfile get(String name) {
        BrowserProfile profile = PROFILES.get(name);
        if (profile == null) {
            throw new IllegalArgumentException("Unknown ui.profile '" + name + "'; known: " + PROFILES.keySet());
        }
        return profile;
    }
}
