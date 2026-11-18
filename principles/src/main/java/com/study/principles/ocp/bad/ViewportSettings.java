package com.study.principles.ocp.bad;

import com.study.principles.ocp.Viewport;

/** Switch #3: this one throws on an unknown name — at run time, in whichever test first asks. */
public final class ViewportSettings {

    private ViewportSettings() {
    }

    public static Viewport forBrowser(String browser) {
        return switch (browser) {
            case "chrome", "firefox", "webkit" -> Viewport.DESKTOP;
            case "mobile-web" -> Viewport.PHONE;
            default -> throw new IllegalArgumentException("unknown browser: " + browser);
        };
    }
}
