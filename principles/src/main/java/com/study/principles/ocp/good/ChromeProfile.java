package com.study.principles.ocp.good;

import com.study.principles.ocp.Browser;
import com.study.principles.ocp.Timeouts;
import com.study.principles.ocp.Viewport;

import java.time.Duration;

public final class ChromeProfile implements BrowserProfile {

    @Override
    public String name() {
        return "chrome";
    }

    @Override
    public Timeouts timeouts() {
        return new Timeouts(Duration.ofSeconds(15), Duration.ofSeconds(5));
    }

    @Override
    public Viewport viewport() {
        return Viewport.DESKTOP;
    }

    @Override
    public Browser launch() {
        return new Browser("chromium", "local", viewport());
    }
}
