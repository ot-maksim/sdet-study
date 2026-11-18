package com.study.principles.ocp.good;

import com.study.principles.ocp.Browser;
import com.study.principles.ocp.Timeouts;
import com.study.principles.ocp.Viewport;

import java.time.Duration;

public final class FirefoxProfile implements BrowserProfile {

    @Override
    public String name() {
        return "firefox";
    }

    @Override
    public Timeouts timeouts() {
        return new Timeouts(Duration.ofSeconds(20), Duration.ofSeconds(7));
    }

    @Override
    public Viewport viewport() {
        return Viewport.DESKTOP;
    }

    @Override
    public Browser launch() {
        return new Browser("firefox", "local", viewport());
    }
}
