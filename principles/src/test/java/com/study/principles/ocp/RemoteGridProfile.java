package com.study.principles.ocp;

import com.study.principles.ocp.good.BrowserProfile;

import java.time.Duration;

/**
 * A profile from "another module": it lives in the test sources and is registered through
 * {@code META-INF/services/com.study.principles.ocp.good.BrowserProfile}. Nothing in {@code main} was edited
 * to add it — extension without modification, literally.
 */
public final class RemoteGridProfile implements BrowserProfile {

    @Override
    public String name() {
        return "remote";
    }

    @Override
    public Timeouts timeouts() {
        return new Timeouts(Duration.ofSeconds(60), Duration.ofSeconds(15));   // network hop to the grid
    }

    @Override
    public Viewport viewport() {
        return Viewport.DESKTOP;
    }

    @Override
    public Browser launch() {
        return new Browser("chromium", "http://grid:4444", viewport());
    }
}
