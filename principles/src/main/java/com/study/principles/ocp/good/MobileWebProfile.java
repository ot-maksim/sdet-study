package com.study.principles.ocp.good;

import com.study.principles.ocp.Browser;
import com.study.principles.ocp.Timeouts;
import com.study.principles.ocp.Viewport;

import java.time.Duration;

public final class MobileWebProfile implements BrowserProfile {

    @Override
    public String name() {
        return "mobile-web";
    }

    @Override
    public Timeouts timeouts() {
        return new Timeouts(Duration.ofSeconds(25), Duration.ofSeconds(8));
    }

    @Override
    public Viewport viewport() {
        return Viewport.PHONE;
    }

    @Override
    public Browser launch() {
        return new Browser("webkit", "local", viewport());
    }
}
